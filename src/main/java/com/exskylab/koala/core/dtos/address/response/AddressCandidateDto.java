package com.exskylab.koala.core.dtos.address.response;

import java.util.UUID;

public record AddressCandidateDto(UUID countryId, String countryName, String countryCode,
                                  UUID cityId, String cityName, UUID districtId, String districtName,
                                  AddressComponentsDto components, AddressPositionDto position,
                                  String candidateToken, boolean needsUserReview, String attribution) {
}
