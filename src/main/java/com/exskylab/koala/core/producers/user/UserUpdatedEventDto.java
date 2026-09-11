package com.exskylab.koala.core.producers.user;


import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class UserUpdatedEventDto {
    private UUID intentId;
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String gsmNumber;
    private String address;
    private String iban;
    private String identityNumber;
    private String iyzicoSubMerchantKey;
}

