package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.address.request.AddressWriteDto;

import java.util.UUID;

public interface AddressCandidateVerifier {
    void verifyAndConsume(UUID userId, AddressWriteDto request);
}
