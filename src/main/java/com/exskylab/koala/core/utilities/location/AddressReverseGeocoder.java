package com.exskylab.koala.core.utilities.location;

import java.math.BigDecimal;
import java.util.Optional;

public interface AddressReverseGeocoder {
    Optional<ProviderAddressCandidate> reverse(BigDecimal latitude, BigDecimal longitude);
}
