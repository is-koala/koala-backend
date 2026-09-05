package com.exskylab.koala.core.security;

import com.exskylab.koala.core.properties.StaffTokenProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StaffAuthenticationManagerResolver implements AuthenticationManagerResolver<HttpServletRequest> {

    private final JwtIssuerAuthenticationManagerResolver delegate;

    public StaffAuthenticationManagerResolver(StaffTokenProperties properties,
                                              StaffJwtAuthenticationConverter converter) {

        Map<String, AuthenticationManager> trusted = new ConcurrentHashMap<>();

        this.delegate = new JwtIssuerAuthenticationManagerResolver(issuer -> {
            if (!properties.isConfigured() || !properties.getIssuerUri().equals(issuer)) {
                return null;
            }
            return trusted.computeIfAbsent(issuer, trustedIssuer -> managerFor(properties, converter));
        });
    }

    private static AuthenticationManager managerFor(StaffTokenProperties properties,
                                                    StaffJwtAuthenticationConverter converter) {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoderFor(properties));
        provider.setJwtAuthenticationConverter(converter);
        return new ProviderManager(provider);
    }

    private static JwtDecoder decoderFor(StaffTokenProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(properties.resolveJwkSetUri())
                .build();

        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                new JwtIssuerValidator(properties.getIssuerUri()),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        audience -> audience != null && audience.contains(properties.getAudience()))));

        return decoder;
    }

    @Override
    public AuthenticationManager resolve(HttpServletRequest request) {
        return delegate.resolve(request);
    }
}
