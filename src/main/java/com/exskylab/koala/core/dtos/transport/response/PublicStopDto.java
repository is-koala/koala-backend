package com.exskylab.koala.core.dtos.transport.response;

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
public class PublicStopDto {

    @Schema(description = "Durağın ilçesi.")
    private String districtName;

    @Schema(types = {"string", "null"}, description = "Durağın mahallesi/semti.")
    private String neighborhood;

    @Schema(description = "Servisin bu duraktan kalkacağı an (UTC).")
    private Instant scheduledAt;
}
