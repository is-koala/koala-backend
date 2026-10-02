package com.exskylab.koala.core.dtos.transport.request;

import com.exskylab.koala.core.constants.TransportMessages;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ShuttleBoardingDto {

    @NotBlank(message = TransportMessages.TOKEN_NOT_BLANK)
    private String token;

    @NotNull(message = TransportMessages.CAPTURED_AT_NOT_NULL)
    private Instant capturedAt;

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private BigDecimal latitude;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private BigDecimal longitude;

    private UUID clientEventId;
}
