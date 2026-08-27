package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.reference.response.CityDto;
import com.exskylab.koala.core.dtos.reference.response.CountryDto;
import com.exskylab.koala.core.dtos.reference.response.DistrictDto;
import com.exskylab.koala.entities.City;
import com.exskylab.koala.entities.Country;
import com.exskylab.koala.entities.District;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ReferenceDataMapper {

    CountryDto toCountryDto(Country country);

    List<CountryDto> toCountryDtoList(List<Country> countries);

    CityDto toCityDto(City city);

    List<CityDto> toCityDtoList(List<City> cities);

    DistrictDto toDistrictDto(District district);

    List<DistrictDto> toDistrictDtoList(List<District> districts);
}
