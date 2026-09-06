package com.exskylab.koala.core.utilities.iys;

import com.exskylab.koala.core.properties.IysProperties;
import com.exskylab.koala.entities.NotificationChannel;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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
            throw new IllegalStateException("IYS provider is not configured");
        }

        Authentication authentication = new Authentication(properties.getApiKey(), properties.getApiHash());
        ConsentRecord item = new ConsentRecord(consent.recipient(), "BIREYSEL", typeOf(consent.channel()),
                consent.granted() ? "ONAY" : "RET", properties.getSource(),
                CONSENT_DATE.format(consent.recordedAt().atZone(ZoneId.of("Europe/Istanbul"))));
        Request request = new Request(authentication,
                new ConsentRegistration(properties.getBrandCode(), List.of(item)));

        IletiMerkeziResponse response = post("/create/json", request);

        if (response == null || response.response() == null || response.response().status() == null
                || !Integer.valueOf(200).equals(response.response().status().code())) {
            throw new IllegalStateException("IYS provider rejected the consent request");
        }
    }

    @Override
    public IysPermissionResult lookupConsent(String recipient, NotificationChannel channel) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("IYS provider is not configured");
        }

        Request request = new Request(
                new Authentication(properties.getApiKey(), properties.getApiHash()),
                new ConsentLookup(properties.getBrandCode(), recipient, "BIREYSEL", typeOf(channel)));
        IletiMerkeziResponse response = post("/show/json", request);
        ProviderConsent consent = response == null || response.response() == null
                ? null : response.response().consent();
        if (response == null || response.response() == null || response.response().status() == null
                || !Integer.valueOf(200).equals(response.response().status().code()) || consent == null) {
            throw new IllegalStateException("IYS provider did not return a consent record");
        }

        boolean granted = switch (consent.status()) {
            case "ONAY" -> true;
            case "RET" -> false;
            default -> throw new IllegalStateException("IYS provider returned an unknown consent status");
        };
        return new IysPermissionResult(consent.recipient(), channel, granted,
                LocalDateTime.parse(consent.consentDate(), CONSENT_DATE)
                        .atZone(ZoneId.of("Europe/Istanbul")).toInstant(), consent.source());
    }

    private IletiMerkeziResponse post(String path, Request request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForObject(properties.getApiUrl() + path,
                new HttpEntity<>(new Envelope(request), headers), IletiMerkeziResponse.class);
    }

    private String typeOf(NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> "EPOSTA";
            case SMS -> "MESAJ";
            case PUSH, IN_APP -> throw new IllegalArgumentException("Unsupported IYS channel: " + channel);
        };
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

    private record ProviderConsent(String recipient, String recipientType, String type, String status,
                                   String source, String consentDate) {
    }
}
