package com.exskylab.koala.core.operations;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalRunbookContractTest {

    @Test
    void backupDryRunIsExecutableAndNeverPrintsCredentialValues() throws Exception {
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
        assertTrue(output.contains("retention-copies"));
        assertTrue(output.contains("success-metric"));
        assertFalse(output.contains("contract-password"));
        assertFalse(output.contains("contract-kms-key"));
    }

    @Test
    void operationalArtifactsCoverRestoreAlertsAndTenGoogleKeys() throws Exception {
        String restore = Files.readString(Path.of("docs/runbooks/restore.md"));
        String alerts = Files.readString(Path.of("ops/monitoring/prometheus-alerts.yml"));
        String inventory = Files.readString(Path.of("docs/secrets-inventory.md"));

        assertTrue(restore.contains("RPO"));
        assertTrue(restore.contains("RTO"));
        assertTrue(restore.contains("7 daily / 4 weekly / 6 monthly"));
        assertTrue(alerts.contains("KoalaHighHttp5xxRate"));
        assertTrue(alerts.contains("KoalaBackupStale"));
        assertEquals(10, inventory.lines().filter(line -> line.startsWith("| GOOGLE_KEY_")).count());
    }
}
