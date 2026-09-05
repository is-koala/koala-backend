package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.properties.TrustedProxyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {

    private ClientIpResolver resolver;

    @BeforeEach
    void setUp() {
        TrustedProxyProperties properties = new TrustedProxyProperties();
        properties.setTrustedProxies(List.of("10.0.0.0/8", "2001:db8::1"));
        resolver = new ClientIpResolver(properties);
    }

    @Test
    void aDirectClientCannotSpoofXForwardedFor() {
        MockHttpServletRequest request = request("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.7");

        assertEquals("203.0.113.10", resolver.resolve(request));
    }

    @Test
    void aTrustedProxyCanForwardTheOriginalClient() {
        MockHttpServletRequest request = request("10.1.2.3");
        request.addHeader("X-Forwarded-For", "198.51.100.7");

        assertEquals("198.51.100.7", resolver.resolve(request));
    }

    @Test
    void aTrustedProxyCanUseTheStandardForwardedHeader() {
        MockHttpServletRequest request = request("2001:db8::1");
        request.addHeader("Forwarded", "for=\"[2001:db8:abcd::7]:443\";proto=https");

        assertEquals("2001:db8:abcd::7", resolver.resolve(request));
    }

    private MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
