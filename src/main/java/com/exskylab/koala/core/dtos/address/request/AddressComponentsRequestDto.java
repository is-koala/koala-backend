package com.exskylab.koala.core.dtos.address.request;

import com.exskylab.koala.core.constants.AddressMessages;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AddressComponentsRequestDto {

    @NotBlank(message = AddressMessages.NEIGHBORHOOD_NOT_BLANK)
    @Size(max = 120, message = AddressMessages.NEIGHBORHOOD_MAX_LENGTH)
    private String neighborhood;

    @NotBlank(message = AddressMessages.THOROUGHFARE_NOT_BLANK)
    @Size(max = 160, message = AddressMessages.THOROUGHFARE_MAX_LENGTH)
    private String thoroughfare;

    @Size(max = 120, message = AddressMessages.SITE_NAME_MAX_LENGTH)
    private String siteName;

    @Size(max = 40, message = AddressMessages.BLOCK_MAX_LENGTH)
    private String block;

    @Size(max = 120, message = AddressMessages.BUILDING_NAME_MAX_LENGTH)
    private String buildingName;

    @Size(max = 30, message = AddressMessages.BUILDING_NUMBER_MAX_LENGTH)
    private String buildingNumber;

    @Size(max = 20, message = AddressMessages.FLOOR_MAX_LENGTH)
    private String floor;

    @Size(max = 30, message = AddressMessages.UNIT_NUMBER_MAX_LENGTH)
    private String unitNumber;

    @NotBlank(message = AddressMessages.POSTAL_CODE_INVALID)
    @Pattern(regexp = "\\d{5}", message = AddressMessages.POSTAL_CODE_INVALID)
    private String postalCode;

    @Size(max = 500, message = AddressMessages.ADDITIONAL_DIRECTIONS_MAX_LENGTH)
    private String additionalDirections;

    @JsonIgnore
    @AssertTrue(message = AddressMessages.LOCATION_DETAIL_REQUIRED)
    public boolean isLocationDetailPresent() {
        return hasText(buildingNumber) || hasText(siteName);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
