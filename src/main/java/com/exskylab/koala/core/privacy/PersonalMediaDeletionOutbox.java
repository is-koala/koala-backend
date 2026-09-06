package com.exskylab.koala.core.privacy;

import com.exskylab.koala.core.utilities.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class PersonalMediaDeletionOutbox {

    private final JdbcTemplate jdbcTemplate;
    private final StorageService storageService;

    public PersonalMediaDeletionOutbox(JdbcTemplate jdbcTemplate, StorageService storageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.storageService = storageService;
    }

    public void enqueue(List<String> keys) {
        for (String key : keys) {
            jdbcTemplate.update("""
                    insert into account_media_deletion_outbox
                        (id, storage_key, attempts, next_attempt_at, created_at)
                    values (?, ?, 0, null, now())
                    on conflict (storage_key) do nothing
                    """, UUID.randomUUID(), key);
        }
    }

    public int processPending(int batchSize) {
        List<String> keys = jdbcTemplate.queryForList("""
                select storage_key from account_media_deletion_outbox
                 where next_attempt_at is null or next_attempt_at <= now()
                 order by next_attempt_at nulls first, created_at
                 limit ?
                """, String.class, batchSize);
        return process(keys);
    }

    public int process(List<String> keys) {
        int deleted = 0;
        for (String key : keys) {
            try {
                storageService.deleteFile(key);
                jdbcTemplate.update(
                        "delete from account_media_deletion_outbox where storage_key = ?", key);
                deleted++;
            } catch (RuntimeException exception) {
                jdbcTemplate.update("""
                        update account_media_deletion_outbox
                           set attempts = attempts + 1,
                               next_attempt_at = now() + interval '1 hour'
                         where storage_key = ?
                        """, key);
                log.error("Personal media deletion failed and will be retried: {}",
                        exception.getClass().getSimpleName());
            }
        }
        return deleted;
    }
}
