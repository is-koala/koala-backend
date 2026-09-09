package com.exskylab.koala.business.concretes;

import java.util.Map;
import java.util.UUID;

record PushDeliveryClaim(
        UUID notificationId,
        UUID claimId,
        UUID recipientId,
        UUID deviceId,
        String token,
        String title,
        String body,
        Map<String, String> data,
        int attempt
) {
}
