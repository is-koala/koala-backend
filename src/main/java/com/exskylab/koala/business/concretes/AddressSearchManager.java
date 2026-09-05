package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.AddressCandidateVerifier;
import com.exskylab.koala.business.abstracts.AddressSearchService;
import com.exskylab.koala.business.abstracts.LocationService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.AddressMessages;
import com.exskylab.koala.core.dtos.address.request.AddressWriteDto;
import com.exskylab.koala.core.dtos.address.request.ConfirmedPositionSource;
import com.exskylab.koala.core.dtos.address.response.AddressComponentsDto;
import com.exskylab.koala.core.dtos.address.response.AddressPositionDto;
import com.exskylab.koala.core.dtos.address.response.AddressCandidateDto;
import com.exskylab.koala.core.dtos.address.response.AddressSearchSessionDto;
import com.exskylab.koala.core.dtos.address.response.AddressSuggestionDto;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.properties.AddressSearchProperties;
import com.exskylab.koala.core.utilities.location.AddressSearchProvider;
import com.exskylab.koala.core.utilities.location.ProviderAddressCandidate;
import com.exskylab.koala.core.utilities.location.IstanbulServiceArea;
import com.exskylab.koala.entities.AddressPositionSource;
import com.exskylab.koala.entities.City;
import com.exskylab.koala.entities.Country;
import com.exskylab.koala.entities.District;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AddressSearchManager implements AddressSearchService, AddressCandidateVerifier {
    private static final int MINIMUM_QUERY_LENGTH = 3;
    private static final int MAXIMUM_QUERY_LENGTH = 160;
    private static final int MAXIMUM_SUGGESTIONS = 5;

    private final AddressSearchProvider provider;
    private final SecurityService securityService;
    private final LocationService locationService;
    private final AddressSearchProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom;
    private final Map<UUID, SearchSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, CandidateProof> candidateProofs = new ConcurrentHashMap<>();

    @Autowired
    public AddressSearchManager(AddressSearchProvider provider, SecurityService securityService,
                                LocationService locationService, AddressSearchProperties properties) {
        this(provider, securityService, locationService, properties, Clock.systemUTC(), new SecureRandom());
    }

    AddressSearchManager(AddressSearchProvider provider, SecurityService securityService,
                         LocationService locationService, AddressSearchProperties properties,
                         Clock clock, SecureRandom secureRandom) {
        this.provider = provider;
        this.securityService = securityService;
        this.locationService = locationService;
        this.properties = properties;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    @Override
    public AddressSearchSessionDto startSession() {
        purgeExpired();
        Instant expiresAt = clock.instant().plus(properties.getSessionTtl());
        SearchSession session = new SearchSession(
                UUID.randomUUID(), securityService.getAuthenticatedUserId(), UUID.randomUUID().toString(), expiresAt);
        sessions.put(session.id, session);
        return new AddressSearchSessionDto(session.id, expiresAt);
    }

    @Override
    public List<AddressSuggestionDto> search(UUID sessionId, String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() < MINIMUM_QUERY_LENGTH || normalizedQuery.length() > MAXIMUM_QUERY_LENGTH) {
            throw new ValidationException(AddressMessages.SEARCH_QUERY_INVALID);
        }
        SearchSession session = requireSession(sessionId);
        List<com.exskylab.koala.core.utilities.location.ProviderAddressSuggestion> suggestions =
                provider.autocomplete(normalizedQuery, session.providerToken);

        synchronized (session) {
            if (session.completed || session.resolving) {
                throw new ValidationException(AddressMessages.SEARCH_SESSION_INVALID);
            }
            return suggestions.stream().limit(MAXIMUM_SUGGESTIONS).map(suggestion -> {
                UUID publicId = session.providerIds.computeIfAbsent(suggestion.id(), ignored -> UUID.randomUUID());
                return new AddressSuggestionDto(publicId, suggestion.mainText(), suggestion.secondaryText());
            }).toList();
        }
    }

    @Override
    public AddressCandidateDto resolve(UUID sessionId, UUID candidateId) {
        SearchSession session = requireSession(sessionId);
        String providerId;
        synchronized (session) {
            if (session.completed || session.resolving) {
                throw new ValidationException(AddressMessages.SEARCH_SESSION_INVALID);
            }
            providerId = session.providerIds.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(candidateId))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow(() -> new ValidationException(AddressMessages.SEARCH_CANDIDATE_INVALID));
            session.resolving = true;
        }

        ProviderAddressCandidate providerCandidate;
        ResolvedReferences references;
        try {
            providerCandidate = provider.resolve(providerId, session.providerToken);
            references = resolveReferences(providerCandidate);
        } catch (RuntimeException exception) {
            synchronized (session) {
                session.resolving = false;
            }
            throw exception;
        }
        String candidateToken = randomToken();
        BigDecimal latitude = coordinate(providerCandidate.latitude());
        BigDecimal longitude = coordinate(providerCandidate.longitude());
        candidateProofs.put(candidateToken, new CandidateProof(
                session.userId, latitude, longitude, ConfirmedPositionSource.AUTOCOMPLETE_CONFIRMED,
                clock.instant().plus(properties.getCandidateTtl())));

        synchronized (session) {
            session.completed = true;
            session.resolving = false;
        }
        sessions.remove(session.id, session);

        AddressComponentsDto components = new AddressComponentsDto(
                valueOrEmpty(providerCandidate.neighborhood()),
                valueOrEmpty(providerCandidate.thoroughfare()),
                providerCandidate.siteName(), null, null,
                providerCandidate.buildingNumber(), null, null,
                valueOrEmpty(providerCandidate.postalCode()), null);
        boolean needsReview = components.getNeighborhood().isBlank()
                || components.getThoroughfare().isBlank()
                || components.getPostalCode().isBlank()
                || (isBlank(components.getBuildingNumber()) && isBlank(components.getSiteName()));

        return new AddressCandidateDto(
                references.country.getId(), references.country.getName(), references.country.getCode(),
                references.city.getId(), references.city.getName(),
                references.district.getId(), references.district.getName(),
                components,
                new AddressPositionDto(latitude, longitude, AddressPositionSource.AUTOCOMPLETE_CONFIRMED),
                candidateToken, needsReview, "Google Maps");
    }

    @Override
    public void verifyAndConsume(UUID userId, AddressWriteDto request) {
        ConfirmedPositionSource source = request.getPosition().getSource();
        String token = request.getCandidateToken();
        if (source == ConfirmedPositionSource.USER_CONFIRMED_PIN) {
            if (token != null && !token.isBlank()) {
                throw new ValidationException(AddressMessages.CANDIDATE_TOKEN_INVALID);
            }
            return;
        }
        if (token == null || token.isBlank()) {
            throw new ValidationException(AddressMessages.CANDIDATE_TOKEN_INVALID);
        }

        CandidateProof proof = candidateProofs.remove(token);
        BigDecimal latitude = coordinate(request.getPosition().getLatitude());
        BigDecimal longitude = coordinate(request.getPosition().getLongitude());
        if (proof == null || !proof.userId.equals(userId) || !proof.expiresAt.isAfter(clock.instant())
                || proof.source != source || proof.latitude.compareTo(latitude) != 0
                || proof.longitude.compareTo(longitude) != 0) {
            throw new ValidationException(AddressMessages.CANDIDATE_TOKEN_INVALID);
        }
    }

    private SearchSession requireSession(UUID sessionId) {
        purgeExpired();
        SearchSession session = sessions.get(sessionId);
        UUID userId = securityService.getAuthenticatedUserId();
        if (session == null || !session.userId.equals(userId) || session.completed
                || !session.expiresAt.isAfter(clock.instant())) {
            throw new ValidationException(AddressMessages.SEARCH_SESSION_INVALID);
        }
        return session;
    }

    private ResolvedReferences resolveReferences(ProviderAddressCandidate candidate) {
        if (!IstanbulServiceArea.supportsCountry(candidate.countryCode())) {
            throw new ValidationException(AddressMessages.SEARCH_OUTSIDE_SERVICE_AREA);
        }
        Country country = locationService.getAllCountries().stream()
                .filter(item -> "TR".equalsIgnoreCase(item.getCode()))
                .findFirst()
                .orElseThrow(() -> new ValidationException(AddressMessages.SEARCH_OUTSIDE_SERVICE_AREA));
        City city = locationService.getCitiesByCountryId(country.getId()).stream()
                .filter(item -> IstanbulServiceArea.supportsCity(item.getName()))
                .filter(item -> IstanbulServiceArea.supportsCity(candidate.city()))
                .findFirst()
                .orElseThrow(() -> new ValidationException(AddressMessages.SEARCH_OUTSIDE_SERVICE_AREA));
        String districtName = IstanbulServiceArea.canonicalName(candidate.district());
        if ("EMINONU".equals(districtName)) {
            districtName = "FATIH";
        }
        String expectedDistrict = districtName;
        District district = locationService.getDistrictsByCityId(city.getId()).stream()
                .filter(item -> expectedDistrict.equals(IstanbulServiceArea.canonicalName(item.getName())))
                .findFirst()
                .orElseThrow(() -> new ValidationException(AddressMessages.SEARCH_OUTSIDE_SERVICE_AREA));
        return new ResolvedReferences(country, city, district);
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        sessions.entrySet().removeIf(entry -> !entry.getValue().expiresAt.isAfter(now));
        candidateProofs.entrySet().removeIf(entry -> !entry.getValue().expiresAt.isAfter(now));
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static BigDecimal coordinate(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static final class SearchSession {
        private final UUID id;
        private final UUID userId;
        private final String providerToken;
        private final Instant expiresAt;
        private final Map<String, UUID> providerIds = new HashMap<>();
        private boolean completed;
        private boolean resolving;

        private SearchSession(UUID id, UUID userId, String providerToken, Instant expiresAt) {
            this.id = id;
            this.userId = userId;
            this.providerToken = providerToken;
            this.expiresAt = expiresAt;
        }
    }

    private record CandidateProof(UUID userId, BigDecimal latitude, BigDecimal longitude,
                                  ConfirmedPositionSource source, Instant expiresAt) {
    }

    private record ResolvedReferences(Country country, City city, District district) {
    }
}
