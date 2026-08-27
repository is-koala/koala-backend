package com.exskylab.koala.core.dtos.jobAssignment.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PaymentSessionDto {

    private String paymentPageUrl;

    private String token;

}
