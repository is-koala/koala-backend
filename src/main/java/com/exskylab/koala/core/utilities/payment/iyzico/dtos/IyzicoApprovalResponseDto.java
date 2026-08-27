package com.exskylab.koala.core.utilities.payment.iyzico.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class IyzicoApprovalResponseDto {
    private String status;
    private String paymentTransactionId;
}
