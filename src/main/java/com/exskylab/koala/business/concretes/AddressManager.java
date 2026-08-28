package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.AddressService;
import com.exskylab.koala.core.constants.AddressMessages;
import com.exskylab.koala.core.dtos.address.request.CreateAddressRequestDto;
import com.exskylab.koala.dataAccess.AddressDao;
import com.exskylab.koala.entities.Address;
import com.exskylab.koala.business.abstracts.LocationService;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AddressManager implements AddressService {

    private final AddressDao addressDao;
    private final LocationService locationService;

    public AddressManager(AddressDao addressDao, LocationService locationService) {
        this.addressDao = addressDao;
        this.locationService = locationService;
    }

    @Override
    public Address createAddress(CreateAddressRequestDto createAddressRequestDto) {
        log.debug("Creating address");
        var country = locationService.findCountryByCode(createAddressRequestDto.getCountryCode());
        var city = locationService.findCityByNameAndCountry(createAddressRequestDto.getCityName(), country);
        var district = locationService.findDistrictByNameAndCity(createAddressRequestDto.getDistrictName(), city);

        Address address = new Address();
        address.setCountry(country);
        address.setCity(city);
        address.setDistrict(district);
        address.setAddressLine(createAddressRequestDto.getAddressLine());
        address.setPostalCode(createAddressRequestDto.getPostalCode());
        address.setLatitude(createAddressRequestDto.getLatitude());
        address.setLongitude(createAddressRequestDto.getLongitude());


        Address savedAddress = addressDao.save(address);

        log.info("Saved address with id: {}", savedAddress.getId());

        return savedAddress;
    }
}
