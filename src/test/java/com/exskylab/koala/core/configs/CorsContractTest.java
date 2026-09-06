package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.properties.CorsProperties;
import com.exskylab.koala.core.properties.RealtimeProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
