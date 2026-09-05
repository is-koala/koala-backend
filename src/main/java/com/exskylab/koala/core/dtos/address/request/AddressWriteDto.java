package com.exskylab.koala.core.dtos.address.request;

import com.exskylab.koala.core.constants.AddressMessages;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "AddressWriteV2")
public class AddressWriteDto {

    @NotNull(message = AddressMessages.COUNTRY_NOT_NULL)
    private UUID countryId;

    @NotNull(message = AddressMessages.CITY_NOT_NULL)
    private UUID cityId;

    @NotNull(message = AddressMessages.DISTRICT_NOT_NULL)
    private UUID districtId;

    @Valid
    @NotNull(message = AddressMessages.COMPONENTS_NOT_NULL)
    private AddressComponentsRequestDto components;

    @Valid
    @NotNull(message = AddressMessages.POSITION_NOT_NULL)
    private AddressPositionRequestDto position;

    @Size(max = 128, message = AddressMessages.CANDIDATE_TOKEN_MAX_LENGTH)
    private String candidateToken;

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignoredValue) {
        throw new IllegalArgumentException("Unsupported address field: " + fieldName);
    }
}
