package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.address.response.AddressDto;
import com.exskylab.koala.entities.Address;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AddressMapper {

    @Mapping(target = "countryName", source = "city.country.name")
    @Mapping(target = "countryCode", source = "city.country.code")
    @Mapping(target = "cityName", source = "city.name")
    @Mapping(target = "districtName", source = "district.name")
    AddressDto toAddressDto(Address address);
}
