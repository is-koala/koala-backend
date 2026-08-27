package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.LocationService;
import com.exskylab.koala.core.constants.LocationMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.dataAccess.CityDao;
import com.exskylab.koala.dataAccess.CountryDao;
import com.exskylab.koala.dataAccess.DistrictDao;
import com.exskylab.koala.entities.City;
import com.exskylab.koala.entities.Country;
import com.exskylab.koala.entities.District;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class LocationManager implements LocationService {

    private final CountryDao countryDao;
    private final CityDao cityDao;
    private final DistrictDao districtDao;

    private final Logger logger = LoggerFactory.getLogger(LocationManager.class);


    public LocationManager(CountryDao countryDao, CityDao cityDao, DistrictDao districtDao) {
        this.countryDao = countryDao;
        this.cityDao = cityDao;
        this.districtDao = districtDao;
    }

    @Override
    public List<Country> getAllCountries() {
        logger.debug("Getting all countries");
        return countryDao.findCountriesByOrderByNameAsc();
    }

    @Override
    public Country getCountryById(UUID id) {
        logger.debug("Getting country with id: {}", id);
        return countryDao.findById(id).orElseThrow(() -> new ResourceNotFoundException(LocationMessages.COUNTRY_NOT_FOUND_BY_ID));
    }

    @Override
    public List<City> getCitiesByCountryId(UUID countryId) {
        logger.debug("Getting cities for country with id: {}", countryId);
        return cityDao.findCitiesByCountryIdOrderByNameAsc(countryId);
    }

    @Override
    public City getCityById(UUID id) {
        logger.debug("Getting city with id: {}", id);
        return cityDao.findById(id).orElseThrow(() -> new ResourceNotFoundException(LocationMessages.CITY_NOT_FOUND_BY_ID));
    }

    @Override
    public List<District> getDistrictsByCityId(UUID cityId) {
        logger.debug("Getting districts for city with id: {}", cityId);
        return districtDao.findDistrictsByCityIdOrderByNameAsc(cityId);
    }

    @Override
    public District getDistrictById(UUID id) {
        logger.debug("Getting district with id: {}", id);
        return districtDao.findById(id).orElseThrow(() -> new ResourceNotFoundException(LocationMessages.DISTRICT_NOT_FOUND_BY_ID));
    }

    @Override
    public Country findCountryByCodeAndName(String countryCode, String countryName) {
        logger.debug("Finding country by code: {} and name: {}", countryCode, countryName);
        return countryDao.findByCodeAndNameAllIgnoreCase(countryCode, countryName)
                .orElseThrow(() -> new ResourceNotFoundException(LocationMessages.COUNTRY_NOT_FOUND_BY_CODE_AND_NAME));

    }

    @Override
    public City findCityByNameAndCountry(String cityName, Country country) {
        logger.debug("Finding city by name: {} and country id: {}", cityName, country.getId());
        return cityDao.findByNameAndCountryAllIgnoreCase(cityName, country)
                .orElseThrow(() -> new ResourceNotFoundException(LocationMessages.CITY_NOT_FOUND_BY_NAME_AND_COUNTRY));
    }

    @Override
    public District findDistrictByNameAndCity(String districtName, City city) {
        logger.debug("Finding district by name: {} and city id: {}", districtName, city.getId());
        return districtDao.findByNameAndCityAllIgnoreCase(districtName, city)
                .orElseThrow(() -> new ResourceNotFoundException(LocationMessages.DISTRICT_NOT_FOUND_BY_NAME_AND_CITY));
    }
}
