package com.exskylab.koala.core.operations;

import com.exskylab.koala.core.monitoring.PaymentMetrics;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalRunbookContractTest {

    private static final String[] RETENTION_TIERS = {"daily", "weekly", "monthly"};

    @Test
    void backupDryRunCoversLogicalAndPhysicalRecoveryWithoutPrintingCredentialValues() throws Exception {
        Path script = Path.of("ops/backup/postgres-backup.sh");
        ProcessBuilder builder = new ProcessBuilder("bash", script.toString(), "--dry-run");
        Map<String, String> environment = builder.environment();
        environment.put("BACKUP_DATABASE_URL", "postgresql://user:contract-password@db/koala");
        environment.put("BACKUP_S3_URI", "s3://koala-contract-backups");
        environment.put("BACKUP_KMS_KEY_ID", "contract-kms-key");
        environment.put("BACKUP_ENVIRONMENT", "contract");
        Process process = builder.redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertEquals(0, process.waitFor(), output);
        assertTrue(output.contains("logical-backup"));
        assertTrue(output.contains("physical-base-backup"));
        assertTrue(output.contains("retention-copies"));
        assertTrue(output.contains("success-metric"));
        assertFalse(output.contains("contract-password"));
        assertFalse(output.contains("contract-kms-key"));
    }

    @Test
    void restoreCanSelectEveryRetentionTierAndPitrHasAnExecutableRecoveryPath() throws Exception {
        for (String tier : RETENTION_TIERS) {
            ProcessBuilder builder = new ProcessBuilder("bash", "ops/backup/restore-latest.sh",
                    "--dry-run", "--tier", tier);
            Map<String, String> environment = builder.environment();
            environment.put("RESTORE_DATABASE_URL", "postgresql://user:contract-password@db/koala");
            environment.put("BACKUP_S3_URI", "s3://koala-contract-backups");
            environment.put("BACKUP_ENVIRONMENT", "contract");
            Process process = builder.redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            assertEquals(0, process.waitFor(), output);
            assertTrue(output.contains("retention=" + tier), output);
            assertFalse(output.contains("contract-password"), output);
        }

        String backup = Files.readString(Path.of("ops/backup/postgres-backup.sh"));
        String restorePitr = Files.readString(Path.of("ops/backup/restore-pitr.sh"));

        assertTrue(backup.contains("pg_basebackup"));
        assertTrue(backup.contains("weekly_checksum_key"));
        assertTrue(backup.contains("monthly_checksum_key"));
        assertTrue(restorePitr.contains("recovery.signal"));
        assertTrue(restorePitr.contains("recovery_target_time"));
        assertTrue(restorePitr.contains("restore-wal.sh"));
    }

    @Test
    void theAbandonedInitiationTheSweepRecordsIsActuallyCoveredByAnAlert() throws Exception {
        assertOutcomeIsWatchedByAnAlert(PaymentMetrics::initiationAbandoned, "initiation", "abandoned");
        assertTrue(alertRules().contains("KoalaPaymentInitiationAbandoned"));
    }

    @Test
    void theEvidenceMismatchOutcomeTheReconciliationRecordsIsCoveredByAnAlert() throws Exception {
        assertOutcomeIsWatchedByAnAlert(PaymentMetrics::payoutSettlementEvidenceMismatch,
                "payout_settlement", "evidence_mismatch");
        assertTrue(alertRules().contains("KoalaPayoutSettlementEvidenceMismatch"));
    }

    @Test
    void thePayoutStuckWaitingForBankEvidenceIsActuallyCoveredByAnAlert() throws Exception {
        assertGaugeIsWatchedByAnAlert(metrics -> metrics.payoutsAwaitingSettlement(3),
                "koala.payout.settlement.overdue");
        assertGaugeIsWatchedByAnAlert(metrics -> metrics.payoutsStalledInSettlement(1),
                "koala.payout.settlement.stalled");

        String alerts = alertRules();
        assertTrue(alerts.contains("KoalaPayoutSettlementOverdue"));
        assertTrue(alerts.contains("KoalaPayoutSettlementStalled"));

        String responses = Files.readString(Path.of("docs/runbooks/alerts.md"));
        assertTrue(responses.contains("KoalaPayoutSettlementOverdue"),
                "Alarmin ilk mudahalesi alerts.md'de yazili olmali.");
        assertTrue(responses.contains("KoalaPayoutSettlementStalled"),
                "Ikinci kademe ilkinden farkli bir ilk mudahale istiyor; yoksa yeni alarm yalnizca gurultu.");
    }

    private static void assertOutcomeIsWatchedByAnAlert(Consumer<PaymentMetrics> record,
                                                        String operation, String outcome) throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        record.accept(new PaymentMetrics(registry));

        Meter recorded = registry.getMeters().stream()
                .filter(meter -> meter.getId().getName().equals("koala.payment.operations"))
                .filter(meter -> operation.equals(meter.getId().getTag("operation")))
                .filter(meter -> outcome.equals(meter.getId().getTag("outcome")))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        operation + "/" + outcome + " bir sayac yazmali; yoksa alarm izleyecek bir sey bulamaz."));

        String series = recorded.getId().getName().replace('.', '_') + "_total";
        String alerts = alertRules();

        List<String> matching = alerts.lines()
                .filter(line -> line.contains(series))
                .filter(line -> line.contains("operation=\"" + operation + "\"")
                        || line.contains("operation=~\"" + operation + "\""))
                .filter(line -> line.contains("outcome=\"" + outcome + "\"")
                        || line.contains("outcome=~\"" + outcome + "\""))
                .toList();

        assertFalse(matching.isEmpty(),
                series + "{operation=" + operation + ",outcome=" + outcome + "} yaziliyor ama hicbir alarm "
                        + "kurali bu seriyi izlemiyor; olay kimseye ulasmaz. Kurallar: " + alerts);
    }

    private static void assertGaugeIsWatchedByAnAlert(Consumer<PaymentMetrics> publish,
                                                      String gaugeName) throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        publish.accept(new PaymentMetrics(registry));

        Meter recorded = registry.getMeters().stream()
                .filter(meter -> meter.getId().getName().equals(gaugeName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        gaugeName + " bir metrige yazilmali; yoksa alarm izleyecek bir sey bulamaz."));

        String series = recorded.getId().getName().replace('.', '_');
        String alerts = alertRules();

        assertTrue(alerts.lines().anyMatch(line -> line.contains(series)),
                "Zamanlayici " + series + " yaziyor ama hicbir alarm kurali bu seriyi izlemiyor; "
                        + "PROCESSING'de takilan hakedis kimseye ulasmaz. Kurallar: " + alerts);
    }

    private static String alertRules() throws Exception {
        return Files.readString(Path.of("ops/monitoring/prometheus-alerts.yml"));
    }

    @Test
    void operationalArtifactsCoverRestoreAlertsAndEnvironmentSpecificGoogleKeys() throws Exception {
        String restore = Files.readString(Path.of("docs/runbooks/restore.md"));
        String alerts = Files.readString(Path.of("ops/monitoring/prometheus-alerts.yml"));
        String inventory = Files.readString(Path.of("docs/secrets-inventory.md"));

        assertTrue(restore.contains("RPO"));
        assertTrue(restore.contains("RTO"));
        assertTrue(restore.contains("7 daily / 4 weekly / 6 monthly"));
        assertTrue(alerts.contains("KoalaHighHttp5xxRate"));
        assertTrue(alerts.contains("KoalaBackupStale"));
        assertTrue(alerts.contains("koala_payment_operations_total"));
        assertTrue(alerts.contains("increase(koala_payment_operations_total"));
        assertTrue(alerts.contains("three_ds_verification_failed"));
        assertTrue(alerts.contains("[5m])) > 0"));
        assertTrue(alerts.contains("email\\\\.queue|sms\\\\.queue|push\\\\.queue"));
        assertEquals(12, inventory.lines().filter(line -> line.startsWith("| GOOGLE_KEY_")).count());
    }
}
