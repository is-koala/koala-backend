package com.exskylab.koala.core.operations;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaunchCutoverRunbookContractTest {

    private static final Path RUNBOOK = Path.of("docs/runbooks/launch-cutover.md");
    private static final Path WIZARD = Path.of("ops/launch/launch-cutover-wizard.sh");

    @Test
    void runbookCoversTheLaunchSafetyContract() throws Exception {
        String runbook = Files.readString(RUNBOOK, StandardCharsets.UTF_8);

        assertAll(
                () -> assertTrue(runbook.contains("RESTORE_CONFIRM_EMPTY_TARGET")),
                () -> assertTrue(runbook.contains("qa_marketplace_seed_versions")),
                () -> assertTrue(runbook.contains("MAIL_DELIVERY_MODE")),
                () -> assertTrue(runbook.contains("IYZICO_BASE_URL")),
                () -> assertTrue(runbook.contains("sessions")),
                () -> assertTrue(runbook.contains("RabbitMQ")),
                () -> assertTrue(runbook.contains("user.profile.updated.for.sync.queue")),
                () -> assertTrue(runbook.contains("settlement_tasks")),
                () -> assertTrue(runbook.contains("iys_consent_tasks")),
                () -> assertTrue(runbook.contains("account_media_deletion_outbox")),
                () -> assertTrue(runbook.contains("media")),
                () -> assertTrue(runbook.contains("ADR-0005")),
                () -> assertTrue(runbook.contains("ADR-0016")),
                () -> assertTrue(runbook.contains("database restore **yasaktır**")),
                () -> assertTrue(runbook.contains("Rollback")),
                () -> assertTrue(runbook.contains("CI veya deploy")));
    }

    @Test
    void wizardRequiresExplicitApprovalWithoutHandlingSecretsOrMutatingProduction() throws Exception {
        String wizard = Files.readString(WIZARD, StandardCharsets.UTF_8);
        String stages = wizard.substring(wizard.indexOf("TOTAL_STAGES=9"));
        String journey = stages.substring(stages.indexOf("banner \""));
        int observationApproval = stages.indexOf("Has the 24-hour observation window completed");
        int finalDecision = stages.indexOf("ROLLBACK_DECISION");
        int livePaymentGate = stages.lastIndexOf("stage \"iyzico live configuration gate\"");
        int productionStartupGate = stages.lastIndexOf("stage \"Clean database and deploy\"");

        assertAll(
                () -> assertTrue(wizard.contains("TOTAL_STAGES=9")),
                () -> assertTrue(stages.contains("--close-observation")),
                () -> assertTrue(stages.contains("ask_required()")),
                () -> assertFalse(journey.lines().anyMatch(line -> line.startsWith("ask "))),
                () -> assertTrue(stages.contains("confirm \"")),
                () -> assertTrue(stages.contains("CUTOVER_APPROVAL_ID")),
                () -> assertTrue(stages.contains("RESTORE_EVIDENCE_ID")),
                () -> assertTrue(stages.contains("QUIET_POINT_LEGACY_BACKUP_EVIDENCE_ID")),
                () -> assertTrue(stages.contains("CLEAN_PRODUCTION_BASELINE_BACKUP_ID")),
                () -> assertTrue(stages.contains("SMTP_UNRESTRICTED_APPROVAL_ID")),
                () -> assertTrue(stages.contains("SMTP_UNRESTRICTED_EVIDENCE_ID")),
                () -> assertTrue(stages.contains("OBSERVATION_COMPLETED_AT_UTC")),
                () -> assertTrue(stages.contains("OBSERVATION_DURATION_EVIDENCE_ID")),
                () -> assertTrue(stages.contains("TRAFFIC_SWITCH_EVIDENCE_ID")),
                () -> assertTrue(stages.contains("TARGET_DATABASE_CONFIRMATION")),
                () -> assertTrue(stages.contains("ROLLBACK_DECISION")),
                () -> assertTrue(observationApproval >= 0 && observationApproval < finalDecision),
                () -> assertTrue(livePaymentGate >= 0 && livePaymentGate < productionStartupGate),
                () -> assertTrue(stages.contains("write_env")),
                () -> assertFalse(stages.contains("ask_secret")),
                () -> assertFalse(stages.contains("set_secret")),
                () -> assertFalse(stages.contains("docker compose down")),
                () -> assertFalse(stages.contains("dropdb")),
                () -> assertTrue(unapprovedExecutableLines(stages).isEmpty(),
                        () -> "Wizard production komutu calistirmamali: " + unapprovedExecutableLines(stages)));
    }

    @Test
    void wizardRejectsAnEmptyRequiredValueBeforeOpeningOrWritingAnything() throws Exception {
        Path evidence = Path.of("target/launch-cutover-empty-input.env");
        Files.deleteIfExists(evidence);
        ProcessBuilder builder = new ProcessBuilder("bash", WIZARD.toString());
        builder.environment().put("ENV_FILE", evidence.toString());
        Process process = builder.redirectErrorStream(true).start();
        process.getOutputStream().write("\n\n".getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().close();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertAll(
                () -> assertTrue(process.waitFor() != 0, output),
                () -> assertTrue(output.contains("CUTOVER_REHEARSAL_ID is required"), output),
                () -> assertFalse(Files.exists(evidence)));
    }

    @Test
    void observationClosureResumesWithoutReplayingDestructiveCutoverStages() throws Exception {
        Path evidence = Path.of("target/launch-cutover-resume.env");
        Files.writeString(evidence, completeManifest("2026-09-08T07:00:00Z"), StandardCharsets.UTF_8);

        ProcessBuilder builder = new ProcessBuilder("bash", WIZARD.toString(), "--close-observation");
        builder.environment().put("ENV_FILE", evidence.toString());
        Process process = builder.redirectErrorStream(true).start();
        process.getOutputStream().write(String.join("\n", List.of(
                "",
                "y",
                "2026-09-09T07:05:00Z",
                "dashboard-123",
                "duration-123",
                "y",
                "PROCEED",
                "")).getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().close();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertAll(
                () -> assertTrue(process.waitFor() == 0, output),
                () -> assertTrue(output.contains("Observation closure"), output),
                () -> assertFalse(output.contains("Clean database and deploy"), output),
                () -> assertFalse(output.contains("SMTP recipient gate"), output),
                () -> assertTrue(Files.readString(evidence).contains("ROLLBACK_DECISION=PROCEED"), output));
    }

    @Test
    void observationClosureRejectsMalformedUtcTimestamps() throws Exception {
        Path evidence = Path.of("target/launch-cutover-invalid-time.env");
        Files.writeString(evidence, completeManifest("not-a-timestamp"), StandardCharsets.UTF_8);

        ProcessBuilder builder = new ProcessBuilder("bash", WIZARD.toString(), "--close-observation");
        builder.environment().put("ENV_FILE", evidence.toString());
        Process process = builder.redirectErrorStream(true).start();
        process.getOutputStream().write("\n".getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().close();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertAll(
                () -> assertTrue(process.waitFor() != 0, output),
                () -> assertTrue(output.contains("TRAFFIC_SWITCHED_AT_UTC must be UTC ISO-8601"), output),
                () -> assertFalse(output.contains("Clean database and deploy"), output));
    }

    @Test
    void observationClosureFailsClosedWhenTheCutoverManifestIsIncomplete() throws Exception {
        Path evidence = Path.of("target/launch-cutover-incomplete.env");
        Files.writeString(
                evidence,
                completeManifest("2026-09-08T07:00:00Z").replace("MIGRATION_EVIDENCE_ID=migration-123", ""),
                StandardCharsets.UTF_8);

        ProcessBuilder builder = new ProcessBuilder("bash", WIZARD.toString(), "--close-observation");
        builder.environment().put("ENV_FILE", evidence.toString());
        Process process = builder.redirectErrorStream(true).start();
        process.getOutputStream().close();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertAll(
                () -> assertTrue(process.waitFor() != 0, output),
                () -> assertTrue(output.contains("MIGRATION_EVIDENCE_ID is missing"), output),
                () -> assertFalse(output.contains("Observation closure"), output));
    }

    private String completeManifest(String trafficTimestamp) {
        return String.join("\n", List.of(
                "DOKPLOY_DASHBOARD_URL=https://dokploy.koala.test",
                "CUTOVER_REHEARSAL_ID=rehearsal-123",
                "CUTOVER_APPROVAL_ID=approval-123",
                "ROLLBACK_OWNER=incident-commander-on-call",
                "TARGET_API_SERVICE=koala-api-production",
                "TARGET_DATABASE_SERVICE=koala-db-production",
                "TARGET_DATABASE_NAME=koala-production",
                "TARGET_RABBITMQ_VHOST=koala-production",
                "TARGET_MEDIA_BUCKET=koala-production/evidence",
                "LEGACY_INVENTORY_ID=inventory-123",
                "PRE_CUTOVER_BACKUP_EVIDENCE_ID=backup-123",
                "RESTORE_EVIDENCE_ID=restore-123",
                "QUIESCE_EVIDENCE_ID=quiesce-123",
                "QUIET_POINT_LEGACY_BACKUP_EVIDENCE_ID=quiet-backup-123",
                "PAYMENT_EVIDENCE_ID=payment-123",
                "TARGET_DATABASE_CONFIRMATION=koala-db-production/koala-production",
                "IMAGE_DIGEST=sha256:" + "a".repeat(64),
                "MIGRATION_EVIDENCE_ID=migration-123",
                "CLEAN_PRODUCTION_BASELINE_BACKUP_ID=baseline-123",
                "SMTP_EVIDENCE_ID=smtp-allowlist-123",
                "SMTP_MODE_DECISION=UNRESTRICTED",
                "SMTP_UNRESTRICTED_APPROVAL_ID=smtp-approval-123",
                "SMTP_UNRESTRICTED_EVIDENCE_ID=smtp-unrestricted-123",
                "TRAFFIC_ROLLBACK_CONTROL=maintenance-switch-123",
                "TRAFFIC_SWITCHED_AT_UTC=" + trafficTimestamp,
                "TRAFFIC_SWITCH_EVIDENCE_ID=change-123",
                "SMOKE_EVIDENCE_ID=smoke-123",
                "OBSERVATION_STARTED_AT_UTC=2026-09-08T07:05:00Z")) + "\n";
    }

    private Set<String> unapprovedExecutableLines(String stages) {
        Set<String> approvedPrefixes = Set.of(
                "TOTAL_STAGES=", "[[ ", "banner ", "ask_required()", "ask_required ", "local ", "ask ",
                "value=", "load_existing_required()", "load_existing_required ",
                "validate_utc_timestamp()", "validate_utc_timestamp ", "close_observation()", "close_observation",
                "for ", "do", "done", "printf -v ", "MODE=",
                "if [[ ", "elif [[ ", "else", "fi", "}", "warn ", "exit ", "stage ",
                "say ", "step ", "open_url ", "confirm ", "write_env ", "finish");

        return stages.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .filter(line -> !line.startsWith("#"))
                .filter(line -> approvedPrefixes.stream().noneMatch(line::startsWith))
                .collect(java.util.stream.Collectors.toSet());
    }
}
