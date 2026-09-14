package com.exskylab.koala.core.dtos.address.response;

import com.exskylab.koala.entities.AddressCandidatePrecision;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record AddressCandidateDto(UUID countryId, String countryName, String countryCode,
                                  UUID cityId, String cityName, UUID districtId, String districtName,
                                  AddressComponentsDto components, AddressPositionDto position,
                                  @Schema(description = "Sağlayıcının bu konum için bulabildiği en ince düzey: "
                                          + "BUILDING bina numarası ya da site adı var, STREET yalnız cadde/sokak var, "
                                          + "NEIGHBORHOOD yalnız mahalle/posta kodu var. İstemci STREET ve NEIGHBORHOOD'da "
                                          + "kullanıcıdan bina numarasını ister.")
                                  AddressCandidatePrecision precision,
                                  String candidateToken, boolean needsUserReview, String attribution) {
}
