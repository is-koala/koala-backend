package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.helpers.RateLimiter;
import com.exskylab.koala.core.properties.RateLimitProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RateLimitBucketTest {

    private RateLimitProperties properties;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        filter = new RateLimitFilter(new RateLimiter(properties), properties,
                mock(HandlerExceptionResolver.class));
    }

    private MockHttpServletRequest request(String method, String uri, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(ip);
        return request;
    }

    private int passing(String method, String uri, String ip, int attempts) throws Exception {
        int passed = 0;
        for (int i = 0; i < attempts; i++) {
            FilterChain call = mock(FilterChain.class);
            filter.doFilter(request(method, uri, ip), new MockHttpServletResponse(), call);
            try {
                verify(call).doFilter(any(), any());
                passed++;
            } catch (AssertionError refused) {
                continue;
            }
        }
        return passed;
    }

    @Test
    void readsAreNotLimited() throws Exception {
        assertEquals(200, passing("GET", "/jobs", "10.0.0.1", 200));
    }

    @Test
    void uploadsShareOneAllowanceAcrossDifferentResources() throws Exception {
        properties.getUpload().setMaxAttempts(3);

        int passed = 0;
        for (int i = 0; i < 6; i++) {
            FilterChain call = mock(FilterChain.class);
            filter.doFilter(request("POST", "/jobs/" + UUID.randomUUID() + "/images", "10.0.0.2"),
                    new MockHttpServletResponse(), call);
            try {
                verify(call).doFilter(any(), any());
                passed++;
            } catch (AssertionError refused) {
                continue;
            }
        }

        assertEquals(3, passed);
    }

    @Test
    void writesAndUploadsDoNotDrainEachOther() throws Exception {
        properties.getUpload().setMaxAttempts(1);
        properties.getWrite().setMaxAttempts(5);

        passing("POST", "/images", "10.0.0.3", 3);

        assertEquals(5, passing("POST", "/jobs/" + UUID.randomUUID() + "/close", "10.0.0.3", 8));
    }

    @Test
    void messagesHaveTheirOwnAllowance() throws Exception {
        properties.getMessage().setMaxAttempts(2);
        properties.getWrite().setMaxAttempts(50);

        assertEquals(2, passing("POST", "/conversations/" + UUID.randomUUID() + "/messages", "10.0.0.4", 6));
    }

    @Test
    void everyWriteEndpointIsCoveredEvenIfNobodyListedIt() throws Exception {
        properties.getWrite().setMaxAttempts(2);

        assertEquals(2, passing("DELETE", "/users/me/blocks/" + UUID.randomUUID(), "10.0.0.5", 5));
    }

    @Test
    void loginKeepsItsOwnPerPathAllowance() throws Exception {
        properties.setMaxAttempts(2);

        assertEquals(2, passing("POST", "/auth/login", "10.0.0.6", 5));
        assertEquals(2, passing("POST", "/auth/forgot-password", "10.0.0.6", 5));
    }

    @Test
    void twoClientsDoNotShareAnAllowance() throws Exception {
        properties.getWrite().setMaxAttempts(2);

        assertEquals(2, passing("POST", "/reports", "10.0.0.7", 5));
        assertEquals(2, passing("POST", "/reports", "10.0.0.8", 5));
    }
}
