package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.properties.CorsProperties;
import com.exskylab.koala.core.properties.RealtimeProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CorsContractTest {

    @Test
    void webOriginsCannotCallTheBackendRestApiDirectly() throws Exception {
        CorsProperties properties = new CorsProperties();
        var filter = new CorsConfig(properties, new RealtimeProperties()).corsFilter();
        var chain = mock(FilterChain.class);

        for (String origin : List.of(
                "https://iskoala.com", "https://admin.iskoala.com", "http://localhost:3000")) {
            var request = new MockHttpServletRequest("OPTIONS", "/jobs");
            request.addHeader("Origin", origin);
            request.addHeader("Access-Control-Request-Method", "GET");
            var response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            assertEquals(403, response.getStatus());
        }
        verifyNoInteractions(chain);
    }

    @Test
    void theRealtimeHandshakeIsNotGovernedByTheRestCorsAllowlist() throws Exception {
        RealtimeProperties realtimeProperties = new RealtimeProperties();
        var filter = new CorsConfig(new CorsProperties(), realtimeProperties).corsFilter();
        var chain = mock(FilterChain.class);

        var request = new MockHttpServletRequest("GET", realtimeProperties.getEndpoint());
        request.addHeader("Origin", "https://iskoala.com");
        request.addHeader("Upgrade", "websocket");
        request.addHeader("Connection", "Upgrade");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
    }

    @Test
    void theProviderCallbackIsNotGovernedByTheRestCorsAllowlist() throws Exception {
        var filter = new CorsConfig(new CorsProperties(), new RealtimeProperties()).corsFilter();

        for (String origin : List.of("https://sandbox-api.iyzipay.com", "https://api.iyzipay.com", "null")) {
            var chain = mock(FilterChain.class);
            var request = new MockHttpServletRequest("POST", "/payments/callback");
            request.setQueryString("token=provider-token");
            request.setContentType("application/x-www-form-urlencoded");
            request.addHeader("Origin", origin);
            var response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            assertEquals(200, response.getStatus(),
                    "Iyzico'nun ust duzey form POST'u CORS filtresinde kesilmemeli, Origin=" + origin);
            assertNull(response.getHeader("Access-Control-Allow-Origin"),
                    "callback CORS'a acilmiyor, yalnizca filtrenin kapsami disinda kaliyor");
            verify(chain).doFilter(request, response);
        }
    }

    @Test
    void theCallbackReplayOverGetIsOutsideTheAllowlistToo() throws Exception {
        var filter = new CorsConfig(new CorsProperties(), new RealtimeProperties()).corsFilter();
        var chain = mock(FilterChain.class);

        var request = new MockHttpServletRequest("GET", "/payments/callback");
        request.addHeader("Origin", "https://sandbox-api.iyzipay.com");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
    }

    @Test
    void theCallbackExemptionDoesNotLeakToNeighbouringPaths() throws Exception {
        var filter = new CorsConfig(new CorsProperties(), new RealtimeProperties()).corsFilter();
        var chain = mock(FilterChain.class);

        for (String path : List.of("/payments", "/payments/callbacks", "/payments/callback/extra",
                "/payment-quotes", "/jobs")) {
            var request = new MockHttpServletRequest("POST", path);
            request.addHeader("Origin", "https://sandbox-api.iyzipay.com");
            var response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            assertEquals(403, response.getStatus(), "listede olmayan kaynak " + path + " icin reddedilmeli");
        }
        verifyNoInteractions(chain);
    }

    @Test
    void aTrailingSlashIsTrimmedByTheFilterItselfSoSuchAnOriginStillMatches() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowCredentials(true);
        configuration.setAllowedOrigins(List.of("https://iskoala.com/"));

        assertEquals(List.of("https://iskoala.com"), configuration.getAllowedOrigins());
        assertEquals("https://iskoala.com", configuration.checkOrigin("https://iskoala.com"));
    }

    @Test
    void onlyABareStarThrowsOnEveryPreflightAndAHostWildcardSilentlyMatchesNothing() {
        CorsConfiguration star = new CorsConfiguration();
        star.setAllowCredentials(true);
        star.setAllowedOrigins(List.of("*"));

        assertThrows(IllegalArgumentException.class, () -> star.checkOrigin("https://iskoala.com"));

        CorsConfiguration hostWildcard = new CorsConfiguration();
        hostWildcard.setAllowCredentials(true);
        hostWildcard.setAllowedOrigins(List.of("https://*.iskoala.com"));

        assertNull(hostWildcard.checkOrigin("https://app.iskoala.com"));
    }
}
