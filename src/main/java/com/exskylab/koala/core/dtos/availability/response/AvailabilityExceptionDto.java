package com.exskylab.koala.core.dtos.availability.response;

import java.time.LocalDate;
import java.util.UUID;

public record AvailabilityExceptionDto(UUID id, LocalDate date, boolean available, String note) {
}
