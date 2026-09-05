package com.exskylab.koala.core.utilities.location;

import java.math.BigDecimal;

public record ProviderAddressCandidate(String countryCode, String city, String district,
                                       String neighborhood, String thoroughfare, String siteName,
                                       String buildingNumber, String postalCode,
                                       BigDecimal latitude, BigDecimal longitude) {
}
