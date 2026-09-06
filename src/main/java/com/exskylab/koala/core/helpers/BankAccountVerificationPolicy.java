package com.exskylab.koala.core.helpers;

import com.exskylab.koala.entities.BankAccount;
import com.exskylab.koala.entities.BankAccountVerificationStatus;

import java.time.Instant;

public final class BankAccountVerificationPolicy {

    private BankAccountVerificationPolicy() {
    }

    public static BankAccountVerificationStatus effectiveStatus(BankAccount account, Instant now) {
        if (account == null) {
            return BankAccountVerificationStatus.UNVERIFIED;
        }

        BankAccountVerificationStatus stored = account.getVerificationStatus() == null
                ? BankAccountVerificationStatus.UNVERIFIED
                : account.getVerificationStatus();

        if (stored != BankAccountVerificationStatus.PENDING) {
            return stored;
        }

        Instant expiresAt = account.getVerificationExpiresAt();
        return expiresAt != null && !expiresAt.isAfter(now)
                ? BankAccountVerificationStatus.EXPIRED
                : BankAccountVerificationStatus.PENDING;
    }

    public static boolean isVerified(BankAccount account) {
        return account != null
                && account.getVerificationStatus() == BankAccountVerificationStatus.VERIFIED;
    }

    public static boolean canStart(BankAccount account, Instant now) {
        return effectiveStatus(account, now) != BankAccountVerificationStatus.VERIFIED;
    }

    public static boolean awaitsTransfer(BankAccount account, Instant now) {
        return effectiveStatus(account, now) == BankAccountVerificationStatus.PENDING;
    }

    public static int attemptsLeft(BankAccount account, int maxAttempts) {
        int used = account == null ? 0 : account.getVerificationAttempts();
        return Math.max(0, maxAttempts - used);
    }
}
