package com.exskylab.koala.core.dtos.transport.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BoardingQrDto {

    private String token;

    @JsonProperty("static")
    @Schema(name = "static", description = "true ise süre dolmayan sabit QR; tarama FAR_FROM_STOP yanında STATIC_QR bayrağı taşır.")
    private boolean staticQr;

    @Schema(description = "Dönen HMAC penceresinin bitişi; sabit QR'da null.")
    private Instant expiresAt;

    private String uri;
}
