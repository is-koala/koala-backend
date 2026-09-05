package com.exskylab.koala.core.dtos.address.request;

import com.exskylab.koala.core.constants.AddressMessages;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AddressPositionRequestDto {

    @NotNull(message = AddressMessages.LATITUDE_NOT_NULL)
    @DecimalMin(value = "-90.000000", message = AddressMessages.LATITUDE_RANGE)
    @DecimalMax(value = "90.000000", message = AddressMessages.LATITUDE_RANGE)
    @Digits(integer = 2, fraction = 6, message = AddressMessages.LATITUDE_PRECISION)
    private BigDecimal latitude;

    @NotNull(message = AddressMessages.LONGITUDE_NOT_NULL)
    @DecimalMin(value = "-180.000000", message = AddressMessages.LONGITUDE_RANGE)
    @DecimalMax(value = "180.000000", inclusive = false, message = AddressMessages.LONGITUDE_RANGE)
    @Digits(integer = 3, fraction = 6, message = AddressMessages.LONGITUDE_PRECISION)
    private BigDecimal longitude;

    @NotNull(message = AddressMessages.POSITION_SOURCE_NOT_NULL)
    private ConfirmedPositionSource source;
}
