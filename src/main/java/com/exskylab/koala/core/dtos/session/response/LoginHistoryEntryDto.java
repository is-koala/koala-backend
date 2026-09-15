package com.exskylab.koala.core.dtos.session.response;

import java.time.Instant;

public record LoginHistoryEntryDto(Instant at, String ipAddress, String location, boolean success) {
}
