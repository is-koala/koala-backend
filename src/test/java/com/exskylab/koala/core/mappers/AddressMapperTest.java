package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.address.response.AddressDto;
import com.exskylab.koala.entities.Address;
import com.exskylab.koala.entities.City;
import com.exskylab.koala.entities.Country;
import com.exskylab.koala.entities.District;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AddressMapperTest {

    private final AddressMapper mapper = new AddressMapperImpl();

    @Test
    void responseCarriesStableReferenceIdsAlongsideLabels() {
        Country country = new Country();
        country.setId(UUID.randomUUID());
        country.setName("TÜRKİYE");
        country.setCode("TR");
        City city = new City();
        city.setId(UUID.randomUUID());
        city.setName("İSTANBUL");
        city.setCountry(country);
        District district = new District();
        district.setId(UUID.randomUUID());
        district.setName("KADIKÖY");
        district.setCity(city);
        Address address = new Address();
        address.setCountry(country);
        address.setCity(city);
        address.setDistrict(district);
        address.setNeighborhood("Caferağa Mahallesi");
        address.setThoroughfare("Moda Caddesi");
        address.setBuildingNumber("12");
        address.setPostalCode("34710");
        address.setLatitude(new BigDecimal("40.982512"));
        address.setLongitude(new BigDecimal("29.025411"));

        AddressDto result = mapper.toAddressDto(address);

        assertEquals(country.getId(), result.getCountryId());
        assertEquals(city.getId(), result.getCityId());
        assertEquals(district.getId(), result.getDistrictId());
        assertEquals("TÜRKİYE", result.getCountryName());
        assertEquals("İSTANBUL", result.getCityName());
        assertEquals("KADIKÖY", result.getDistrictName());
        assertEquals("Caferağa Mahallesi", result.getComponents().getNeighborhood());
        assertEquals("Moda Caddesi", result.getComponents().getThoroughfare());
        assertEquals("12", result.getComponents().getBuildingNumber());
        assertEquals(new BigDecimal("40.982512"), result.getPosition().getLatitude());
    }

    @Test
    void missingOptionalDistrictProducesNoDistrictReference() {
        Country country = new Country();
        country.setId(UUID.randomUUID());
        City city = new City();
        city.setId(UUID.randomUUID());
        city.setCountry(country);
        Address address = new Address();
        address.setCountry(country);
        address.setCity(city);

        AddressDto result = mapper.toAddressDto(address);

        assertNull(result.getDistrictId());
        assertNull(result.getDistrictName());
    }
}
