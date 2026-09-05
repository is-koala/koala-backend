package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.address.request.AddressWriteDto;
import com.exskylab.koala.entities.Address;

public interface AddressService {
    Address createAddress(AddressWriteDto createAddressRequestDto);
}
