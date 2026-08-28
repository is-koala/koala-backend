package com.exskylab.koala.core.utilities.payment.iyzico.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubMerchantUpdateResponseDto {

    private String status;
    private String locale;
    private String conversationId;
    private String subMerchantKey;

}
