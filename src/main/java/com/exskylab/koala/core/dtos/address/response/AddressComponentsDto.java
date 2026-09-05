package com.exskylab.koala.core.dtos.address.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AddressComponentsDto {
    private String neighborhood;
    private String thoroughfare;
    private String siteName;
    private String block;
    private String buildingName;
    private String buildingNumber;
    private String floor;
    private String unitNumber;
    private String postalCode;
    private String additionalDirections;
}
