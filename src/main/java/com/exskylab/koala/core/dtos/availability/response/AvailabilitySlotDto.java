package com.exskylab.koala.core.dtos.availability.response;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

public record AvailabilitySlotDto(UUID id, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
}
