package com.exskylab.koala.core.privacy;

import com.exskylab.koala.core.properties.IysProperties;
import com.exskylab.koala.core.utilities.iys.IysRecipientCipher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgresAccountAnonymizerTest {

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void personalObjectDeletionRunsOnlyAfterTheDatabaseTransactionCommits() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PersonalMediaDeletionOutbox outbox = mock(PersonalMediaDeletionOutbox.class);
        UUID userId = UUID.randomUUID();
        String key = "cv/private.pdf";
        String evidenceKey = "files/dispute-evidence/9a3b2f11-7f0e-4a3d-8c21-1c5c2f4a77b0.png";
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class).contains("dispute_evidence")
                        ? List.of(evidenceKey)
                        : List.of(key));
        TransactionSynchronizationManager.initSynchronization();

        IysProperties properties = new IysProperties();
        properties.setRecipientEncryptionKey("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        new PostgresAccountAnonymizer(jdbcTemplate, outbox, new IysRecipientCipher(properties), properties)
                .anonymize(userId, UUID.randomUUID());

        verify(outbox).enqueue(List.of(key, evidenceKey));
        verify(outbox, never()).process(List.of(key, evidenceKey));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCommit());
        verify(outbox).process(List.of(key, evidenceKey));
    }
}
