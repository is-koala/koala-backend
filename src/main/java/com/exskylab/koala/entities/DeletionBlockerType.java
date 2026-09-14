package com.exskylab.koala.entities;

import com.exskylab.koala.core.constants.LegalMessages;

public enum DeletionBlockerType {
    PENDING_PAYMENT(LegalMessages.DELETION_BLOCKED_BY_PENDING_PAYMENT),
    ESCROW(LegalMessages.DELETION_BLOCKED_BY_ESCROW),
    ACTIVE_WORK(LegalMessages.DELETION_BLOCKED_BY_ACTIVE_WORK),
    DISPUTE(LegalMessages.DELETION_BLOCKED_BY_DISPUTE);

    private final String messageKey;

    DeletionBlockerType(String messageKey) {
        this.messageKey = messageKey;
    }

    public String getMessageKey() {
        return messageKey;
    }
}
