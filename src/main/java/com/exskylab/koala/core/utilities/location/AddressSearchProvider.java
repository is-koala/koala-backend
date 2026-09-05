package com.exskylab.koala.core.utilities.location;

import java.util.List;

public interface AddressSearchProvider {
    List<ProviderAddressSuggestion> autocomplete(String query, String sessionToken);

    ProviderAddressCandidate resolve(String providerCandidateId, String sessionToken);
}
