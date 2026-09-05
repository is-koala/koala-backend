package com.exskylab.koala.core.dtos.address.response;

import com.exskylab.koala.entities.AddressPositionSource;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AddressPositionDto {
    private BigDecimal latitude;
    private BigDecimal longitude;
    private AddressPositionSource source;
}
