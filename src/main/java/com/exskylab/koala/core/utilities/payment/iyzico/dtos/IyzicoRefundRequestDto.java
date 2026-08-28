package com.exskylab.koala.core.utilities.payment.iyzico.dtos;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class IyzicoRefundRequestDto {
    private String paymentTransactionId;
    private String conversationId;
    private BigDecimal price;
    private String currency = "TRY";
    private String ip;
}
