package com.exskylab.koala.core.utilities.iys;

import com.exskylab.koala.entities.NotificationChannel;

import java.util.UUID;

public record IysReconciliationTarget(UUID userId, NotificationChannel channel) {
}
