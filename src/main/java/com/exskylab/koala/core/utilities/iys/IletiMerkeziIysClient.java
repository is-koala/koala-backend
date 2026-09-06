package com.exskylab.koala.core.utilities.iys;

import com.exskylab.koala.core.properties.IysProperties;
import com.exskylab.koala.core.constants.IysMessages;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.entities.NotificationChannel;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class IletiMerkeziIysClient implements IysProviderClient {

    private static final DateTimeFormatter CONSENT_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Europe/Istanbul"));

    private final RestTemplate restTemplate;
    private final IysProperties properties;

    public IletiMerkeziIysClient(RestTemplate restTemplate, IysProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public void registerConsent(IysConsentRequest consent) {
        if (!properties.isConfigured()) {
            throw new ExternalServiceException(IysMessages.PROVIDER_NOT_CONFIGURED);
        }

        String recipient = normalizedRecipient(consent.recipient(), consent.channel());
        Authentication authentication = new Authentication(properties.getApiKey(), properties.getApiHash());
        ConsentRecord item = new ConsentRecord(recipient, "BIREYSEL", typeOf(consent.channel()),
                consent.granted() ? "ONAY" : "RET", consent.source(),
                CONSENT_DATE.format(consent.recordedAt().atZone(ZoneId.of("Europe/Istanbul"))));
        Request request = new Request(authentication,
                new ConsentRegistration(properties.getBrandCode(), List.of(item)));

        IletiMerkeziResponse response = post("/create/json", request);

        if (response == null || response.response() == null || response.response().status() == null
                || !Integer.valueOf(200).equals(response.response().status().code())) {
            throw new ExternalServiceException(IysMessages.PROVIDER_REJECTED);
        }
    }

    @Override
    public IysPermissionResult lookupConsent(String recipient, NotificationChannel channel) {
        if (!properties.isConfigured()) {
            throw new ExternalServiceException(IysMessages.PROVIDER_NOT_CONFIGURED);
        }

        String requestedRecipient = normalizedRecipient(recipient, channel);
        Request request = new Request(
                new Authentication(properties.getApiKey(), properties.getApiHash()),
                new ConsentLookup(properties.getBrandCode(), requestedRecipient, "BIREYSEL", typeOf(channel)));
        IletiMerkeziResponse response = post("/show/json", request);
        ProviderConsent consent = response == null || response.response() == null
                ? null : response.response().consent();
        if (response == null || response.response() == null || response.response().status() == null
                || !Integer.valueOf(200).equals(response.response().status().code()) || consent == null) {
            throw new ExternalServiceException(IysMessages.INVALID_RESPONSE);
        }

        try {
            String responseRecipient = normalizedRecipient(consent.recipient(), channel);
            if (!requestedRecipient.equalsIgnoreCase(responseRecipient)
                    || !"BIREYSEL".equals(consent.recipientType())
                    || !typeOf(channel).equals(consent.type())
                    || consent.brandCode() == null
                    || !properties.getBrandCode().strip().equals(String.valueOf(consent.brandCode()).strip())
                    || !properties.isAllowedSource(consent.source())) {
                throw new ExternalServiceException(IysMessages.INVALID_RESPONSE);
            }
            boolean granted = switch (consent.status()) {
                case "ONAY" -> true;
                case "RET" -> false;
                default -> throw new ExternalServiceException(IysMessages.INVALID_RESPONSE);
            };
            return new IysPermissionResult(responseRecipient, channel, granted,
                    LocalDateTime.parse(consent.consentDate(), CONSENT_DATE)
                            .atZone(ZoneId.of("Europe/Istanbul")).toInstant(), consent.source());
        } catch (ExternalServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ExternalServiceException(IysMessages.INVALID_RESPONSE);
        }
    }

    private IletiMerkeziResponse post(String path, Request request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            return restTemplate.postForObject(properties.getApiUrl() + path,
                    new HttpEntity<>(new Envelope(request), headers), IletiMerkeziResponse.class);
        } catch (RestClientResponseException exception) {
            throw new ExternalServiceException(IysMessages.PROVIDER_REJECTED);
        } catch (RestClientException exception) {
            throw new ExternalServiceException(IysMessages.PROVIDER_UNAVAILABLE);
        }
    }

    private String typeOf(NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> "EPOSTA";
            case SMS -> "MESAJ";
            case PUSH, IN_APP -> throw new ValidationException(IysMessages.CHANNEL_UNSUPPORTED);
        };
    }

    private String normalizedRecipient(String recipient, NotificationChannel channel) {
        String normalized = IysRecipientNormalizer.normalize(recipient, channel);
        if (normalized == null) {
            throw new ValidationException(IysMessages.VERIFIED_RECIPIENT_REQUIRED);
        }
        return normalized;
    }

    private record Envelope(Request request) {
    }

    private record Request(Authentication authentication, Object consent) {
    }

    private record Authentication(String key, String hash) {
    }

    private record ConsentRegistration(String brandCode, List<ConsentRecord> list) {
    }

    private record ConsentLookup(String brandCode, String recipient, String recipientType, String type) {
    }

    private record ConsentRecord(String recipient, String recipientType, String type, String status,
                                 String source, String consentDate) {
    }

    private record IletiMerkeziResponse(Response response) {
    }

    private record Response(Status status, ProviderConsent consent) {
    }

    private record Status(Integer code, String message) {
    }

    private record ProviderConsent(Object brandCode, String recipient, String recipientType, String type, String status,
                                   String source, String consentDate) {
    }
}
