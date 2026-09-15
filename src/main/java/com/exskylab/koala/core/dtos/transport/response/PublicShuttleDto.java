package com.exskylab.koala.core.dtos.transport.response;

import com.exskylab.koala.entities.TransportDirection;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PublicShuttleDto {

    private TransportDirection direction;

    private String name;

    @Schema(description = "Duraklar ilçe/semt düzeyinde ve saatiyle; tam adres, plaka ve şoför bilgisi taşınmaz.")
    private List<PublicStopDto> stops;
}
