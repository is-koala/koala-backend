package com.exskylab.koala.core.dtos.payment.request;

import com.exskylab.koala.core.constants.JobMessages;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PricingQuoteRequestDto(
        @NotNull(message = JobMessages.MINIMUM_SALARY_NOT_NULL)
        @DecimalMin(value = "1.00", message = JobMessages.SALARY_MIN_VALUE)
        BigDecimal workerPayout) {
}
