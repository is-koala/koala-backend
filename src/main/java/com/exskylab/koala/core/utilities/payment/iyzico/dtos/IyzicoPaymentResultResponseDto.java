package com.exskylab.koala.core.utilities.payment.iyzico.dtos;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IyzicoPaymentResultResponseDto {
    private String status;
    private String paymentId;
    private String paymentTransactionId;
    private String conversationId;
    private String token;
    private String paymentStatus;
    private Integer fraudStatus;
    private Integer mdStatus;
    private BigDecimal price;
    private BigDecimal paidPrice;
    private BigDecimal itemPaidPrice;
    private BigDecimal itemPrice;
    private BigDecimal itemSubMerchantPrice;
    private String itemId;
    private int itemCount;
    private String currency;
    private String basketId;
}
