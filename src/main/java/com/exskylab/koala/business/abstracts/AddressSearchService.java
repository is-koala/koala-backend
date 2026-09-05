package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.address.response.AddressCandidateDto;
import com.exskylab.koala.core.dtos.address.response.AddressSearchSessionDto;
import com.exskylab.koala.core.dtos.address.response.AddressSuggestionDto;
import com.exskylab.koala.core.dtos.address.request.ReverseGeocodeRequestDto;

import java.util.List;
import java.util.UUID;

public interface AddressSearchService {
    AddressSearchSessionDto startSession();

    List<AddressSuggestionDto> search(UUID sessionId, String query);

    AddressCandidateDto resolve(UUID sessionId, UUID candidateId);

    AddressCandidateDto reverse(ReverseGeocodeRequestDto request);
}
