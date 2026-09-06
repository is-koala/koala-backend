package com.exskylab.koala.core.utilities.iys;

import com.exskylab.koala.entities.NotificationChannel;

import java.time.Instant;

public record IysConsentRequest(String recipient, NotificationChannel channel, boolean granted,
                                Instant recordedAt, String source) {
}
