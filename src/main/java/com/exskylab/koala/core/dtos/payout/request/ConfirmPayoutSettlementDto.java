package com.exskylab.koala.core.dtos.payout.request;

import com.exskylab.koala.core.constants.PayoutMessages;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ConfirmPayoutSettlementDto {

    @NotBlank(message = PayoutMessages.SETTLEMENT_REFERENCE_REQUIRED)
    @Size(max = 128, message = PayoutMessages.SETTLEMENT_REFERENCE_REQUIRED)
    private String settlementReference;

    @NotBlank(message = PayoutMessages.PROVIDER_REFERENCE_REQUIRED)
    @Size(max = 128, message = PayoutMessages.PROVIDER_REFERENCE_REQUIRED)
    private String providerReference;

    @NotNull(message = PayoutMessages.SETTLED_AMOUNT_REQUIRED)
    @Digits(integer = 17, fraction = 2, message = PayoutMessages.SETTLED_AMOUNT_REQUIRED)
    @DecimalMin(value = "0.01", message = PayoutMessages.SETTLED_AMOUNT_REQUIRED)
    private BigDecimal amount;

    @NotBlank(message = PayoutMessages.SETTLED_CURRENCY_REQUIRED)
    @Size(min = 3, max = 3, message = PayoutMessages.SETTLED_CURRENCY_REQUIRED)
    private String currency;

    @NotNull(message = PayoutMessages.SETTLED_AT_REQUIRED)
    @PastOrPresent(message = PayoutMessages.SETTLED_AT_REQUIRED)
    private Instant settledAt;
}
