package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.address.response.AddressDto;
import com.exskylab.koala.entities.Address;
import com.exskylab.koala.core.helpers.AddressFormatter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = AddressFormatter.class)
public interface AddressMapper {

    @Mapping(target = "countryName", source = "city.country.name")
    @Mapping(target = "countryCode", source = "city.country.code")
    @Mapping(target = "countryId", source = "city.country.id")
    @Mapping(target = "cityName", source = "city.name")
    @Mapping(target = "cityId", source = "city.id")
    @Mapping(target = "districtName", source = "district.name")
    @Mapping(target = "districtId", source = "district.id")
    @Mapping(target = "components.neighborhood", source = "neighborhood")
    @Mapping(target = "components.thoroughfare", source = "thoroughfare")
    @Mapping(target = "components.siteName", source = "siteName")
    @Mapping(target = "components.block", source = "block")
    @Mapping(target = "components.buildingName", source = "buildingName")
    @Mapping(target = "components.buildingNumber", source = "buildingNumber")
    @Mapping(target = "components.floor", source = "floor")
    @Mapping(target = "components.unitNumber", source = "unitNumber")
    @Mapping(target = "components.postalCode", source = "postalCode")
    @Mapping(target = "components.additionalDirections", source = "additionalDirections")
    @Mapping(target = "position.latitude", source = "latitude")
    @Mapping(target = "position.longitude", source = "longitude")
    @Mapping(target = "position.source", source = "positionSource")
    @Mapping(target = "displayAddress", source = ".", qualifiedByName = "displayAddress")
    AddressDto toAddressDto(Address address);
}
