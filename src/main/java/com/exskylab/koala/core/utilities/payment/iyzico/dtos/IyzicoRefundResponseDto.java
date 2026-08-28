package com.exskylab.koala.core.utilities.payment.iyzico.dtos;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class IyzicoRefundResponseDto {
    private String status;
    private String paymentId;
    private String paymentTransactionId;
    private BigDecimal price;
    private String currency;
    private String authCode;
    private String hostReference;
    private String refundHostReference;
}
