package com.exskylab.koala.core.security;

import com.exskylab.koala.core.properties.StaffTokenProperties;
import com.nimbusds.jwt.JWTParser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;

import java.text.ParseException;

@Component
@Slf4j
public class StaffBearerTokenResolver implements BearerTokenResolver {

    private final DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();

    private final StaffTokenProperties properties;

    public StaffBearerTokenResolver(StaffTokenProperties properties) {
        this.properties = properties;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        if (!properties.isConfigured()) {
            return null;
        }

        String token = delegate.resolve(request);
        if (token == null) {
            return null;
        }

        return properties.getIssuerUri().equals(unverifiedIssuerOf(token)) ? token : null;
    }

    private static String unverifiedIssuerOf(String token) {
        try {
            return JWTParser.parse(token).getJWTClaimsSet().getIssuer();
        } catch (ParseException exception) {
            log.debug("Bearer token bir JWT olarak ayrıştırılamadı, personel zincirine verilmedi");
            return null;
        }
    }
}
