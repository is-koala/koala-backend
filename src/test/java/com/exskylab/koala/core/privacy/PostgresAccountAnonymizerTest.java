package com.exskylab.koala.core.privacy;

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
        when(jdbcTemplate.queryForList(anyString(), eq(String.class),
                any(), any(), any())).thenReturn(List.of(key));
        TransactionSynchronizationManager.initSynchronization();

        new PostgresAccountAnonymizer(jdbcTemplate, outbox)
                .anonymize(userId, UUID.randomUUID());

        verify(outbox).enqueue(List.of(key));
        verify(outbox, never()).process(List.of(key));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCommit());
        verify(outbox).process(List.of(key));
    }
}
