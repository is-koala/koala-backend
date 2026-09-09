package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.utilities.storage.StorageService;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StorageRollbackCleanup {

    public static void deleteOnRollback(StorageService storageService, String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }

                try {
                    storageService.deleteFile(storageKey);
                } catch (RuntimeException exception) {
                    log.error("Orphaned storage object could not be removed after transaction rollback: {}",
                            storageKey, exception);
                }
            }
        });
    }
}
