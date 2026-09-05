package com.exskylab.koala.core.dtos.jobAssignment.response;

import com.exskylab.koala.core.dtos.payment.response.PricingBreakdownDto;
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

    private PricingBreakdownDto pricing;

}
