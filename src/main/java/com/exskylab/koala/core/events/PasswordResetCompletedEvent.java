package com.exskylab.koala.core.events;

import java.util.UUID;

public record PasswordResetCompletedEvent(UUID userId) {
}
