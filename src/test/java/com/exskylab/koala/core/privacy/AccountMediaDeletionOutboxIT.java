package com.exskylab.koala.core.privacy;

import com.exskylab.koala.core.utilities.storage.StorageService;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

class AccountMediaDeletionOutboxIT extends AbstractIntegrationTest {

    @Autowired
    private AccountAnonymizer accountAnonymizer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PersonalMediaDeletionOutbox outbox;

    @MockitoBean
    private StorageService storageService;

    @Test
    void aStorageFailureAfterCommitLeavesADurableRetryRecord() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        String key = "cv/retry-me.pdf";
        jdbcTemplate.update("""
                insert into users (id, first_name, username, email, is_employer, created_at, is_deleted)
                values (?, 'Silinecek', ?, ?, false, now(), false)
                """, userId, "delete-" + userId, "delete-" + userId + "@example.test");
        jdbcTemplate.update("""
                insert into media
                    (id, media_type, file_name, file_url, file_size, created_by_id, created_at, is_deleted)
                values (?, 'FILE', 'cv.pdf', ?, 20, ?, now(), false)
                """, mediaId, key, userId);
        jdbcTemplate.update("update users set cv_id = ? where id = ?", mediaId, userId);
        jdbcTemplate.update("""
                insert into account_deletion_requests
                    (id, user_id, subject_id, status, requested_at, created_at, is_deleted)
                values (?, ?, ?, 'REQUESTED', now(), now(), false)
                """, requestId, userId, userId);
        doThrow(new IllegalStateException("storage unavailable"))
                .when(storageService).deleteFile(key);

        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> accountAnonymizer.anonymize(userId, requestId));

        verify(storageService).deleteFile(key);
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from account_media_deletion_outbox where storage_key = ?",
                Integer.class, key));

        reset(storageService);
        jdbcTemplate.update("""
                update account_media_deletion_outbox set next_attempt_at = now()
                 where storage_key = ?
                """, key);
        assertEquals(1, outbox.processPending(10));

        verify(storageService).deleteFile(key);
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from account_media_deletion_outbox where storage_key = ?",
                Integer.class, key));
    }
}
