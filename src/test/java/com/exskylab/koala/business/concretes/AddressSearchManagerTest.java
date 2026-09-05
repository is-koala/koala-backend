package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.LocationService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.dtos.address.request.AddressComponentsRequestDto;
import com.exskylab.koala.core.dtos.address.request.AddressPositionRequestDto;
import com.exskylab.koala.core.dtos.address.request.AddressWriteDto;
import com.exskylab.koala.core.dtos.address.request.ConfirmedPositionSource;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.properties.AddressSearchProperties;
import com.exskylab.koala.core.utilities.location.AddressSearchProvider;
import com.exskylab.koala.core.utilities.location.AddressReverseGeocoder;
import com.exskylab.koala.core.utilities.location.ProviderAddressCandidate;
import com.exskylab.koala.core.utilities.location.ProviderAddressSuggestion;
import com.exskylab.koala.entities.City;
import com.exskylab.koala.entities.Country;
import com.exskylab.koala.entities.District;
import com.exskylab.koala.entities.AddressPositionSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressSearchManagerTest {
    private static final UUID USER_ID = UUID.fromString("dbec03c1-c921-48ee-a5e8-a79e5716661d");
    private static final Instant NOW = Instant.parse("2026-09-05T12:00:00Z");

    @Mock
    private AddressSearchProvider provider;

    @Mock
    private AddressReverseGeocoder reverseGeocoder;

    @Mock
    private SecurityService securityService;

    @Mock
    private LocationService locationService;

    private AddressSearchManager manager;
    private Country country;
    private City city;
    private District district;

    @BeforeEach
    void setUp() {
        AddressSearchProperties properties = new AddressSearchProperties();
        properties.setSessionTtl(Duration.ofMinutes(5));
        properties.setCandidateTtl(Duration.ofMinutes(10));
        manager = new AddressSearchManager(provider, reverseGeocoder, securityService, locationService, properties,
                Clock.fixed(NOW, ZoneOffset.UTC), new SecureRandom());
        country = new Country();
        country.setId(UUID.randomUUID());
        country.setCode("TR");
        country.setName("TÜRKİYE");
        city = new City();
        city.setId(UUID.randomUUID());
        city.setName("İSTANBUL");
        city.setCountry(country);
        district = new District();
        district.setId(UUID.randomUUID());
        district.setName("KADIKÖY");
        district.setCity(city);
    }

    @Test
    void aResolvedSuggestionReturnsInternalReferencesAndOneUseProof() {
        authenticatedAs(USER_ID);
        var session = manager.startSession();
        when(provider.autocomplete(anyString(), anyString())).thenReturn(List.of(
                new ProviderAddressSuggestion("google-place-1", "Moda Caddesi", "Kadıköy/İstanbul")));
        var suggestion = manager.search(session.id(), "Moda Caddesi").getFirst();
        when(provider.resolve(anyString(), anyString())).thenReturn(new ProviderAddressCandidate(
                "TR", "İstanbul", "Kadıköy", "Caferağa Mahallesi", "Moda Caddesi",
                null, "12", "34710", new BigDecimal("40.982512"), new BigDecimal("29.025411")));
        referencesAreAvailable();

        var candidate = manager.resolve(session.id(), suggestion.id());

        assertEquals(country.getId(), candidate.countryId());
        assertEquals(city.getId(), candidate.cityId());
        assertEquals(district.getId(), candidate.districtId());
        assertEquals("Caferağa Mahallesi", candidate.components().getNeighborhood());
        assertFalse(candidate.needsUserReview());
        assertEquals("Google Maps", candidate.attribution());

        AddressWriteDto write = write(candidate.candidateToken());
        assertDoesNotThrow(() -> manager.verifyAndConsume(USER_ID, write));
        assertThrows(ValidationException.class, () -> manager.verifyAndConsume(USER_ID, write));
    }

    @Test
    void providerCandidateIdsNeverLeaveTheBackend() {
        authenticatedAs(USER_ID);
        var session = manager.startSession();
        when(provider.autocomplete(anyString(), anyString())).thenReturn(List.of(
                new ProviderAddressSuggestion("google-place-secret", "Moda", "İstanbul")));

        var result = manager.search(session.id(), "Moda").getFirst();

        assertFalse(result.id().toString().contains("google"));
    }

    @Test
    void shortQueriesAreRejectedBeforeCallingTheProvider() {
        authenticatedAs(USER_ID);
        var session = manager.startSession();

        assertThrows(ValidationException.class, () -> manager.search(session.id(), "ab"));

        verify(provider, never()).autocomplete(anyString(), anyString());
    }

    @Test
    void providerProofCannotBeUsedByAnotherUser() {
        authenticatedAs(USER_ID);
        var session = manager.startSession();
        when(provider.autocomplete(anyString(), anyString())).thenReturn(List.of(
                new ProviderAddressSuggestion("place-1", "Moda", "İstanbul")));
        var suggestion = manager.search(session.id(), "Moda").getFirst();
        when(provider.resolve(anyString(), anyString())).thenReturn(new ProviderAddressCandidate(
                "TR", "İstanbul", "Kadıköy", "", "Moda Caddesi",
                null, "12", "34710", new BigDecimal("40.982512"), new BigDecimal("29.025411")));
        referencesAreAvailable();
        var candidate = manager.resolve(session.id(), suggestion.id());

        assertTrue(candidate.needsUserReview());
        assertThrows(ValidationException.class, () ->
                manager.verifyAndConsume(UUID.randomUUID(), write(candidate.candidateToken())));
    }

    @Test
    void manualPinsNeedNoProviderProof() {
        AddressWriteDto request = write(null);
        request.getPosition().setSource(ConfirmedPositionSource.USER_CONFIRMED_PIN);

        assertDoesNotThrow(() -> manager.verifyAndConsume(USER_ID, request));
    }

    @Test
    void reverseGeocodingReturnsAUserBoundProofForTheSelectedPin() {
        authenticatedAs(USER_ID);
        when(reverseGeocoder.reverse(new BigDecimal("40.982512"), new BigDecimal("29.025411")))
                .thenReturn(Optional.of(new ProviderAddressCandidate(
                        "TR", "İstanbul", "Kadıköy", "Caferağa Mahallesi", "Moda Caddesi",
                        null, "12", "34710", new BigDecimal("40.982500"), new BigDecimal("29.025400"))));
        referencesAreAvailable();

        var candidate = manager.reverse(new com.exskylab.koala.core.dtos.address.request.ReverseGeocodeRequestDto(
                new BigDecimal("40.982512"), new BigDecimal("29.025411")));

        assertEquals(AddressPositionSource.REVERSE_GEOCODE_CONFIRMED, candidate.position().getSource());
        assertEquals(new BigDecimal("40.982512"), candidate.position().getLatitude());
        AddressWriteDto write = write(candidate.candidateToken());
        write.getPosition().setSource(ConfirmedPositionSource.REVERSE_GEOCODE_CONFIRMED);
        assertDoesNotThrow(() -> manager.verifyAndConsume(USER_ID, write));
    }

    private void referencesAreAvailable() {
        when(locationService.getAllCountries()).thenReturn(List.of(country));
        when(locationService.getCitiesByCountryId(country.getId())).thenReturn(List.of(city));
        when(locationService.getDistrictsByCityId(city.getId())).thenReturn(List.of(district));
    }

    private void authenticatedAs(UUID userId) {
        when(securityService.getAuthenticatedUserId()).thenReturn(userId);
    }

    private AddressWriteDto write(String candidateToken) {
        AddressComponentsRequestDto components = new AddressComponentsRequestDto();
        components.setNeighborhood("Caferağa Mahallesi");
        components.setThoroughfare("Moda Caddesi");
        components.setBuildingNumber("12");
        components.setPostalCode("34710");
        AddressPositionRequestDto position = new AddressPositionRequestDto();
        position.setLatitude(new BigDecimal("40.982512"));
        position.setLongitude(new BigDecimal("29.025411"));
        position.setSource(ConfirmedPositionSource.AUTOCOMPLETE_CONFIRMED);
        AddressWriteDto request = new AddressWriteDto();
        request.setCountryId(country.getId());
        request.setCityId(city.getId());
        request.setDistrictId(district.getId());
        request.setComponents(components);
        request.setPosition(position);
        request.setCandidateToken(candidateToken);
        return request;
    }
}
