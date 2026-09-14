package com.exskylab.koala.core.utilities.location;

import com.exskylab.koala.core.dtos.address.response.AddressCandidatePrecision;

import java.math.BigDecimal;
import java.util.stream.Stream;

public record ProviderAddressCandidate(String countryCode, String city, String district,
                                       String neighborhood, String thoroughfare, String siteName,
                                       String buildingNumber, String postalCode,
                                       BigDecimal latitude, BigDecimal longitude) {

    public AddressCandidatePrecision precision() {
        if (hasText(buildingNumber) || hasText(siteName)) {
            return AddressCandidatePrecision.BUILDING;
        }
        if (hasText(thoroughfare)) {
            return AddressCandidatePrecision.STREET;
        }
        return AddressCandidatePrecision.NEIGHBORHOOD;
    }

    public boolean isMoreDetailedThan(ProviderAddressCandidate other) {
        if (precision() != other.precision()) {
            return precision().isFinerThan(other.precision());
        }
        return filledComponents() > other.filledComponents();
    }

    private long filledComponents() {
        return Stream.of(city, district, neighborhood, thoroughfare, siteName, buildingNumber, postalCode)
                .filter(ProviderAddressCandidate::hasText)
                .count();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
