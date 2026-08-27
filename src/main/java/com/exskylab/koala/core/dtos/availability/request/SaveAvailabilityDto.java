package com.exskylab.koala.core.dtos.availability.request;

import com.exskylab.koala.core.constants.AvailabilityMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SaveAvailabilityDto {

    @NotNull(message = AvailabilityMessages.SLOTS_REQUIRED)
    @Size(max = 50, message = AvailabilityMessages.TOO_MANY_SLOTS)
    @Valid
    private List<Slot> slots;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Slot {

        @NotNull(message = AvailabilityMessages.DAY_REQUIRED)
        private DayOfWeek dayOfWeek;

        @NotNull(message = AvailabilityMessages.TIME_REQUIRED)
        private LocalTime startTime;

        @NotNull(message = AvailabilityMessages.TIME_REQUIRED)
        private LocalTime endTime;
    }
}
