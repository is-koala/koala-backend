package com.exskylab.koala.core.dtos.company.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Şirket ilanının işvereni: kimlik, ad ve logo. Bireysel ilanda null.")
public record CompanySummaryDto(UUID id, String name,
                                @Schema(types = {"string", "null"}, description = "Şirket logosu; yüklenmemişse null.")
                                String logoUrl) {
}
