package com.exskylab.koala.core.properties;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class StompOriginPropertiesTest {

    @Test
    void onlyTheConfiguredExactBrowserOriginsAreAllowed() {
        StompOriginProperties properties = new StompOriginProperties();
        properties.setProduction("https://iskoala.com");
        properties.setStaging("https://staging.iskoala.com");
        CorsConfiguration handshakeOrigins = new CorsConfiguration();
        handshakeOrigins.setAllowedOrigins(properties.allowedOrigins());

        assertEquals("https://iskoala.com", handshakeOrigins.checkOrigin("https://iskoala.com"));
        assertEquals("https://staging.iskoala.com", handshakeOrigins.checkOrigin("https://staging.iskoala.com"));
        assertNull(handshakeOrigins.checkOrigin("https://evil-iskoala.com"));
        assertNull(handshakeOrigins.checkOrigin("https://iskoala.com.evil.example"));
    }

    @Test
    void originlessNativeConnectionsRemainAllowed() {
        StompOriginProperties properties = new StompOriginProperties();
        properties.setProduction("https://iskoala.com");

        assertTrue(properties.allows(null));
    }
}
