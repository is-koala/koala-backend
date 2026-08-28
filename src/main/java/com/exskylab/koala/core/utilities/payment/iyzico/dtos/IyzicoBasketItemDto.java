package com.exskylab.koala.core.utilities.payment.iyzico.dtos;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class IyzicoBasketItemDto {

    private String id;
    private String name;
    private String category1="Hizmet";
    private BigDecimal price;
    private String itemType = "VIRTUAL";
    private String subMerchantKey;
    private BigDecimal subMerchantPrice;

}
