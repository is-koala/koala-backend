package com.exskylab.koala.core.dtos.availability.response;

import java.util.List;

public record AvailabilityDto(List<AvailabilitySlotDto> slots,
                              List<AvailabilityExceptionDto> exceptions) {
}
