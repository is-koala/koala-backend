package com.exskylab.koala.core.dtos.availability.request;

import com.exskylab.koala.core.constants.AvailabilityMessages;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SaveAvailabilityExceptionDto {

    @NotNull(message = AvailabilityMessages.DATE_REQUIRED)
    @FutureOrPresent(message = AvailabilityMessages.DATE_IN_PAST)
    private LocalDate date;

    @NotNull(message = AvailabilityMessages.AVAILABLE_REQUIRED)
    private Boolean available;

    @Size(max = 255)
    private String note;
}
