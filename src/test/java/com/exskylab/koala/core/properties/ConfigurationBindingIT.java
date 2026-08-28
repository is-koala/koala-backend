package com.exskylab.koala.core.properties;

import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationBindingIT extends AbstractIntegrationTest {

    @Autowired
    private AppProperties appProperties;

    @Autowired
    private SessionProperties sessionProperties;

    @Autowired
    private CorsProperties corsProperties;

    @Autowired
    private StorageProperties storageProperties;

    @Autowired
    private IyzicoProperties iyzicoProperties;

    @Autowired
    private Environment environment;

    @Test
    void appPropertiesAreBound() {
        assertNotNull(appProperties.apiUrl());
        assertNotNull(appProperties.frontendUrl());
        assertFalse(appProperties.apiUrl().isBlank());
        assertFalse(appProperties.frontendUrl().isBlank());
    }

    @Test
    void sessionLifetimesComeFromConfiguration() {
        assertTrue(sessionProperties.getRefreshValidityDays() > 0);
        assertTrue(sessionProperties.getRefreshSlidingWindowDays() > 0);
        assertTrue(sessionProperties.getRefreshSlidingWindowDays() < sessionProperties.getRefreshValidityDays(),
                "Kaydirma esigi toplam omurden kucuk olmali, aksi halde her yenilemede omur uzar.");
    }

    @Test
    void corsOriginsAreAListNotAWildcard() {
        assertFalse(corsProperties.getAllowedOrigins().isEmpty());
        assertFalse(corsProperties.getAllowedOrigins().contains("*"),
                "Kimlik bilgisi paylasilan bir CORS yapilandirmasinda joker origin kabul edilemez.");
    }

    @Test
    void externalServicePropertiesAreBound() {
        assertEquals("local", storageProperties.getType());
        assertNotNull(storageProperties.getLocal().getPath());
        assertNotNull(iyzicoProperties.getBaseUrl());
    }

    @Test
    void jwtSecretIsLongEnoughForHs256() {
        String secret = environment.getProperty("jwt.secret");
        assertNotNull(secret);
        byte[] decoded = java.util.Base64.getDecoder().decode(secret);
        assertTrue(decoded.length >= 32,
                "HS256 en az 256 bitlik anahtar istiyor; kisa anahtarda uygulama acilista duser.");
    }
}
