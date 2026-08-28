package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.City;
import com.exskylab.koala.entities.Country;
import com.exskylab.koala.entities.District;

import java.util.List;
import java.util.UUID;

public interface LocationService {

    List<Country> getAllCountries();

    Country getCountryById(UUID id);

    List<City> getCitiesByCountryId(UUID countryId);

    City getCityById(UUID id);

    List<District> getDistrictsByCityId(UUID cityId);

    District getDistrictById(UUID id);

    Country findCountryByCodeAndName(String countryCode, String countryName);

    City findCityByNameAndCountry(String cityName, Country country);

    District findDistrictByNameAndCity(String districtName, City city);

    Country findCountryByCode(String countryCode);
}
