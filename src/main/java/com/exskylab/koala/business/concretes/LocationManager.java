package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.business.abstracts.LocationService;
import com.exskylab.koala.core.constants.LocationMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.dataAccess.CityDao;
import com.exskylab.koala.dataAccess.CountryDao;
import com.exskylab.koala.dataAccess.DistrictDao;
import com.exskylab.koala.entities.City;
import com.exskylab.koala.entities.Country;
import com.exskylab.koala.entities.District;
import com.exskylab.koala.core.constants.AddressMessages;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class LocationManager implements LocationService {

    private final CountryDao countryDao;
    private final CityDao cityDao;
    private final DistrictDao districtDao;


    public LocationManager(CountryDao countryDao, CityDao cityDao, DistrictDao districtDao) {
        this.countryDao = countryDao;
        this.cityDao = cityDao;
        this.districtDao = districtDao;
    }

    @Override
    public List<Country> getAllCountries() {
        log.debug("Getting all countries");
        return countryDao.findCountriesByOrderByNameAsc();
    }

    @Override
    public Country getCountryById(UUID id) {
        log.debug("Getting country with id: {}", id);
        return countryDao.findById(id).orElseThrow(() -> new ResourceNotFoundException(LocationMessages.COUNTRY_NOT_FOUND_BY_ID));
    }

    @Override
    public List<City> getCitiesByCountryId(UUID countryId) {
        log.debug("Getting cities for country with id: {}", countryId);
        return cityDao.findCitiesByCountryIdOrderByNameAsc(countryId);
    }

    @Override
    public City getCityById(UUID id) {
        log.debug("Getting city with id: {}", id);
        return cityDao.findById(id).orElseThrow(() -> new ResourceNotFoundException(LocationMessages.CITY_NOT_FOUND_BY_ID));
    }

    @Override
    public List<District> getDistrictsByCityId(UUID cityId) {
        log.debug("Getting districts for city with id: {}", cityId);
        return districtDao.findDistrictsByCityIdOrderByNameAsc(cityId);
    }

    @Override
    public District getDistrictById(UUID id) {
        log.debug("Getting district with id: {}", id);
        return districtDao.findById(id).orElseThrow(() -> new ResourceNotFoundException(LocationMessages.DISTRICT_NOT_FOUND_BY_ID));
    }

    @Override
    public Country findCountryByCodeAndName(String countryCode, String countryName) {
        log.debug("Finding country by code: {} and name: {}", countryCode, countryName);
        return countryDao.findByCodeAndNameAllIgnoreCase(countryCode, countryName)
                .orElseThrow(() -> new ResourceNotFoundException(LocationMessages.COUNTRY_NOT_FOUND_BY_CODE_AND_NAME));

    }

    @Override
    public City findCityByNameAndCountry(String cityName, Country country) {
        log.debug("Finding city by name: {} and country id: {}", cityName, country.getId());
        return cityDao.findByNameAndCountryAllIgnoreCase(cityName, country)
                .orElseThrow(() -> new ResourceNotFoundException(LocationMessages.CITY_NOT_FOUND_BY_NAME_AND_COUNTRY));
    }

    @Override
    public District findDistrictByNameAndCity(String districtName, City city) {
        log.debug("Finding district by name: {} and city id: {}", districtName, city.getId());
        return districtDao.findByNameAndCityAllIgnoreCase(districtName, city)
                .orElseThrow(() -> new ResourceNotFoundException(LocationMessages.DISTRICT_NOT_FOUND_BY_NAME_AND_CITY));
    }

    @Override
    public Country findCountryByCode(String countryCode) {
        return countryDao.findByCodeAllIgnoreCase(countryCode)
                .orElseThrow(() -> new ValidationException(AddressMessages.COUNTRY_NOT_FOUND));
    }
}
