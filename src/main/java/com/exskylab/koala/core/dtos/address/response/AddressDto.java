package com.exskylab.koala.core.dtos.address.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AddressDto {

    private UUID id;

    private UUID countryId;

    private String countryName;

    private String countryCode;

    private UUID cityId;

    private String cityName;

    private UUID districtId;

    private String districtName;

    private AddressComponentsDto components;

    private AddressPositionDto position;

    private String displayAddress;

    private Instant confirmedAt;

    private int canonicalVersion;

    private boolean needsReview;

}
