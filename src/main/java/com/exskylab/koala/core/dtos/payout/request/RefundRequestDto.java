package com.exskylab.koala.core.dtos.payout.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RefundRequestDto {

    @DecimalMin(value = "0.01", message = "{payout.invalid.refund.amount}")
    private BigDecimal amount;

    @Size(max = 512)
    private String reason;
}
