package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.LocationService;
import com.exskylab.koala.business.abstracts.AddressCandidateVerifier;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.dtos.address.request.AddressComponentsRequestDto;
import com.exskylab.koala.core.dtos.address.request.AddressPositionRequestDto;
import com.exskylab.koala.core.dtos.address.request.AddressWriteDto;
import com.exskylab.koala.core.dtos.address.request.ConfirmedPositionSource;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.dataAccess.AddressDao;
import com.exskylab.koala.entities.Address;
import com.exskylab.koala.entities.City;
import com.exskylab.koala.entities.Country;
import com.exskylab.koala.entities.District;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressManagerTest {

    @Mock
    private AddressDao addressDao;

    @Mock
    private LocationService locationService;

    @Mock
    private AddressCandidateVerifier candidateVerifier;

    @Mock
    private SecurityService securityService;

    private AddressManager manager;

    @BeforeEach
    void setUp() {
        manager = new AddressManager(addressDao, locationService, candidateVerifier, securityService);
    }

    @Test
    void structuredReferenceIdsAreResolvedAndPersisted() {
        Country country = country();
        City city = city(country);
        District district = district(city);
        AddressWriteDto request = request(country.getId(), city.getId(), district.getId());

        when(locationService.getCountryById(country.getId())).thenReturn(country);
        when(locationService.getCityById(city.getId())).thenReturn(city);
        when(locationService.getDistrictById(district.getId())).thenReturn(district);
        when(addressDao.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Address saved = manager.createAddress(request);

        assertSame(country, saved.getCountry());
        assertSame(city, saved.getCity());
        assertSame(district, saved.getDistrict());
        org.junit.jupiter.api.Assertions.assertEquals("Caferağa Mahallesi", saved.getNeighborhood());
        org.junit.jupiter.api.Assertions.assertEquals("Moda Caddesi", saved.getThoroughfare());
        org.junit.jupiter.api.Assertions.assertEquals("12", saved.getBuildingNumber());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("40.982512"), saved.getLatitude());
        org.junit.jupiter.api.Assertions.assertEquals(1, saved.getCanonicalVersion());
        org.junit.jupiter.api.Assertions.assertFalse(saved.isNeedsReview());
        org.junit.jupiter.api.Assertions.assertNotNull(saved.getConfirmedAt());
    }

    @Test
    void aCityOutsideTheSelectedCountryIsRejected() {
        Country selectedCountry = country();
        City city = city(country());
        AddressWriteDto request = request(selectedCountry.getId(), city.getId(), null);

        when(locationService.getCountryById(selectedCountry.getId())).thenReturn(selectedCountry);
        when(locationService.getCityById(city.getId())).thenReturn(city);

        assertThrows(ValidationException.class, () -> manager.createAddress(request));
        verify(addressDao, never()).save(any());
        verify(candidateVerifier, never()).verifyAndConsume(any(), any());
    }

    @Test
    void aDistrictOutsideTheSelectedCityIsRejected() {
        Country country = country();
        City city = city(country);
        District district = district(city(country));
        AddressWriteDto request = request(country.getId(), city.getId(), district.getId());

        when(locationService.getCountryById(country.getId())).thenReturn(country);
        when(locationService.getCityById(city.getId())).thenReturn(city);
        when(locationService.getDistrictById(district.getId())).thenReturn(district);

        assertThrows(ValidationException.class, () -> manager.createAddress(request));
        verify(addressDao, never()).save(any());
        verify(candidateVerifier, never()).verifyAndConsume(any(), any());
    }

    @Test
    void aManualPinOutsideTheIstanbulServiceAreaIsRejected() {
        Country country = country();
        City city = city(country);
        District district = district(city);
        AddressWriteDto request = request(country.getId(), city.getId(), district.getId());
        request.getPosition().setLatitude(new BigDecimal("39.925533"));
        request.getPosition().setLongitude(new BigDecimal("32.866287"));

        when(locationService.getCountryById(country.getId())).thenReturn(country);
        when(locationService.getCityById(city.getId())).thenReturn(city);
        when(locationService.getDistrictById(district.getId())).thenReturn(district);

        assertThrows(ValidationException.class, () -> manager.createAddress(request));
        verify(addressDao, never()).save(any());
        verify(candidateVerifier, never()).verifyAndConsume(any(), any());
    }

    private static AddressWriteDto request(UUID countryId, UUID cityId, UUID districtId) {
        AddressWriteDto request = new AddressWriteDto();
        request.setCountryId(countryId);
        request.setCityId(cityId);
        request.setDistrictId(districtId);
        AddressComponentsRequestDto components = new AddressComponentsRequestDto();
        components.setNeighborhood("Caferağa Mahallesi");
        components.setThoroughfare("Moda Caddesi");
        components.setBuildingNumber("12");
        components.setPostalCode("34710");
        request.setComponents(components);
        AddressPositionRequestDto position = new AddressPositionRequestDto();
        position.setLatitude(new BigDecimal("40.982512"));
        position.setLongitude(new BigDecimal("29.025411"));
        position.setSource(ConfirmedPositionSource.USER_CONFIRMED_PIN);
        request.setPosition(position);
        return request;
    }

    private static Country country() {
        Country country = new Country();
        country.setId(UUID.randomUUID());
        country.setCode("TR");
        country.setName("TÜRKİYE");
        return country;
    }

    private static City city(Country country) {
        City city = new City();
        city.setId(UUID.randomUUID());
        city.setName("İSTANBUL");
        city.setCountry(country);
        return city;
    }

    private static District district(City city) {
        District district = new District();
        district.setId(UUID.randomUUID());
        district.setName("KADIKÖY");
        district.setCity(city);
        return district;
    }
}
