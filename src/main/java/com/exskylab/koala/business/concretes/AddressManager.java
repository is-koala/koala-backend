package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.AddressService;
import com.exskylab.koala.business.abstracts.AddressCandidateVerifier;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.AddressMessages;
import com.exskylab.koala.core.dtos.address.request.AddressWriteDto;
import com.exskylab.koala.dataAccess.AddressDao;
import com.exskylab.koala.entities.Address;
import com.exskylab.koala.entities.City;
import com.exskylab.koala.entities.Country;
import com.exskylab.koala.entities.District;
import com.exskylab.koala.entities.AddressPositionSource;
import com.exskylab.koala.business.abstracts.LocationService;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.utilities.location.IstanbulServiceArea;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AddressManager implements AddressService {

    private final AddressDao addressDao;
    private final LocationService locationService;
    private final AddressCandidateVerifier candidateVerifier;
    private final SecurityService securityService;

    public AddressManager(AddressDao addressDao, LocationService locationService,
                          AddressCandidateVerifier candidateVerifier, SecurityService securityService) {
        this.addressDao = addressDao;
        this.locationService = locationService;
        this.candidateVerifier = candidateVerifier;
        this.securityService = securityService;
    }

    @Override
    public Address createAddress(AddressWriteDto addressWriteDto) {
        log.debug("Creating address");
        var references = resolveReferences(addressWriteDto);
        var components = addressWriteDto.getComponents();
        var position = addressWriteDto.getPosition();
        if (!IstanbulServiceArea.supportsCountry(references.country().getCode())
                || !IstanbulServiceArea.supportsCity(references.city().getName())
                || !IstanbulServiceArea.contains(position.getLatitude(), position.getLongitude())) {
            throw new ValidationException(AddressMessages.SEARCH_OUTSIDE_SERVICE_AREA);
        }

        Address address = new Address();
        address.setCountry(references.country());
        address.setCity(references.city());
        address.setDistrict(references.district());
        address.setNeighborhood(normalize(components.getNeighborhood()));
        address.setThoroughfare(normalize(components.getThoroughfare()));
        address.setSiteName(normalize(components.getSiteName()));
        address.setBlock(normalize(components.getBlock()));
        address.setBuildingName(normalize(components.getBuildingName()));
        address.setBuildingNumber(normalize(components.getBuildingNumber()));
        address.setFloor(normalize(components.getFloor()));
        address.setUnitNumber(normalize(components.getUnitNumber()));
        address.setPostalCode(components.getPostalCode());
        address.setAdditionalDirections(normalize(components.getAdditionalDirections()));
        address.setLatitude(position.getLatitude());
        address.setLongitude(position.getLongitude());
        address.setPositionSource(AddressPositionSource.valueOf(position.getSource().name()));
        address.setConfirmedAt(java.time.Instant.now());
        address.setCanonicalVersion(1);
        address.setNeedsReview(false);

        candidateVerifier.verifyAndConsume(securityService.getAuthenticatedUserId(), addressWriteDto);
        Address savedAddress = addressDao.save(address);

        log.info("Saved address with id: {}", savedAddress.getId());

        return savedAddress;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AddressReferences resolveReferences(AddressWriteDto request) {
        var country = locationService.getCountryById(request.getCountryId());
        var city = locationService.getCityById(request.getCityId());
        if (city.getCountry() == null || !country.getId().equals(city.getCountry().getId())) {
            throw new ValidationException(AddressMessages.REFERENCE_MISMATCH);
        }

        var district = locationService.getDistrictById(request.getDistrictId());
        if (district.getCity() == null || !city.getId().equals(district.getCity().getId())) {
            throw new ValidationException(AddressMessages.REFERENCE_MISMATCH);
        }
        return new AddressReferences(country, city, district);
    }

    private record AddressReferences(Country country, City city, District district) {
    }
}
