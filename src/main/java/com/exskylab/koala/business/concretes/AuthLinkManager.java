package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.AuthLinkService;
import com.exskylab.koala.core.properties.AuthLinkProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class AuthLinkManager implements AuthLinkService {

    private static final String COMPLETE_REGISTRATION = "complete-registration";
    private static final String RESET_PASSWORD = "reset-password";

    private final AuthLinkProperties properties;

    public AuthLinkManager(AuthLinkProperties properties) {
        this.properties = properties;
    }

    @Override
    public String registrationLandingLink(String token) {
        return landingLink(COMPLETE_REGISTRATION, token);
    }

    @Override
    public String passwordResetLandingLink(String token) {
        return landingLink(RESET_PASSWORD, token);
    }

    @Override
    public String registrationAppLink(String token) {
        return appLink(COMPLETE_REGISTRATION, token);
    }

    @Override
    public String passwordResetAppLink(String token) {
        return appLink(RESET_PASSWORD, token);
    }

    private String landingLink(String destination, String token) {
        return UriComponentsBuilder.fromUriString(properties.getPublicBaseUrl())
                .pathSegment("auth-links", destination)
                .queryParam("token", token)
                .build()
                .encode()
                .toUriString();
    }

    private String appLink(String destination, String token) {
        return UriComponentsBuilder.newInstance()
                .scheme(properties.getAppScheme())
                .host(destination)
                .queryParam("token", token)
                .build()
                .encode()
                .toUriString();
    }
}
