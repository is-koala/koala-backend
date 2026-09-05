package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.AuthLinkService;
import com.exskylab.koala.core.properties.AuthLinkProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class AuthLinkManager implements AuthLinkService {

    private static final String COMPLETE_REGISTRATION = "complete-registration";
    private static final String RESET_PASSWORD = "reset-password";
    private static final String REGISTRATION_WEB_PATH = "hesap-dogrula";
    private static final String PASSWORD_RESET_WEB_PATH = "sifre-sifirla";
    private static final String[] UNSUBSCRIBE_WEB_PATH = {"bildirim-tercihleri", "cikis"};

    private final AuthLinkProperties properties;

    public AuthLinkManager(AuthLinkProperties properties) {
        this.properties = properties;
    }

    @Override
    public String registrationLandingLink(String token) {
        return landingLink(token, REGISTRATION_WEB_PATH);
    }

    @Override
    public String passwordResetLandingLink(String token) {
        return landingLink(token, PASSWORD_RESET_WEB_PATH);
    }

    @Override
    public String registrationAppLink(String token) {
        return appLink(COMPLETE_REGISTRATION, token);
    }

    @Override
    public String passwordResetAppLink(String token) {
        return appLink(RESET_PASSWORD, token);
    }

    @Override
    public String unsubscribeLandingLink(String token) {
        return landingLink(token, UNSUBSCRIBE_WEB_PATH);
    }

    private String landingLink(String token, String... destination) {
        return UriComponentsBuilder.fromUriString(properties.getPublicBaseUrl())
                .pathSegment(destination)
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
