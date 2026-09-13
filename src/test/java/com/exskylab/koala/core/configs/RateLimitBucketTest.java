package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.helpers.RateLimiter;
import com.exskylab.koala.core.properties.RateLimitProperties;
import com.exskylab.koala.core.helpers.ClientIpResolver;
import com.exskylab.koala.core.properties.TrustedProxyProperties;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.UUID;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class RateLimitBucketTest {

    private RateLimitProperties properties;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        filter = new RateLimitFilter(new RateLimiter(properties), properties,
                mock(HandlerExceptionResolver.class), new ClientIpResolver(new TrustedProxyProperties()),
                new ObjectMapper());
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
        assertEquals(200, passing("GET", "/users/me/notifications", "10.0.0.1", 200));
    }

    @Test
    void publicJobReadsShareALooseButFiniteAllowance() throws Exception {
        properties.getPublic().setMaxAttempts(2);

        assertEquals(2, passing("GET", "/jobs", "10.0.0.11", 5));
        assertEquals(0, passing("GET", "/jobs/" + UUID.randomUUID(), "10.0.0.11", 1));
    }

    @Test
    void sitemapReadsHaveTheirOwnFiniteAllowance() throws Exception {
        properties.getPublic().setMaxAttempts(1);

        assertEquals(30, passing("GET", "/sitemap/jobs/manifest", "10.0.0.12", 31));
        assertEquals(0, passing("GET", "/sitemap/jobs/manifests/" + UUID.randomUUID()
                + "/shards/0", "10.0.0.12", 1));
        assertEquals(1, passing("GET", "/jobs", "10.0.0.12", 2));
    }

    @Test
    void addressAutocompleteHasItsOwnAllowanceIncludingReads() throws Exception {
        properties.getPublic().setMaxAttempts(3);
        properties.getWrite().setMaxAttempts(50);

        assertEquals(3, passing("GET", "/locations/address-search-sessions/"
                + UUID.randomUUID() + "/suggestions", "10.0.0.9", 7));
    }

    @Test
    void reverseGeocodingSharesTheCostControlBucket() throws Exception {
        properties.getPublic().setMaxAttempts(2);

        assertEquals(1, passing("GET", "/locations/address-search-sessions/"
                + UUID.randomUUID() + "/suggestions", "10.0.0.10", 1));
        assertEquals(1, passing("POST", "/locations/address-candidates/reverse", "10.0.0.10", 3));
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
    void companyLogoUsesTheUploadAllowance() throws Exception {
        properties.getUpload().setMaxAttempts(1);
        properties.getWrite().setMaxAttempts(5);

        assertEquals(1, passing("PUT", "/companies/" + UUID.randomUUID() + "/logo", "10.0.0.14", 3));
        assertEquals(5, passing("POST", "/reports", "10.0.0.14", 5));
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
    void realtimeCredentialsHaveTheirOwnAllowance() throws Exception {
        properties.getRealtimeCredential().setMaxAttempts(2);
        properties.getWrite().setMaxAttempts(50);

        assertEquals(2, passing("POST", "/users/me/realtime-credential", "10.0.0.13", 5));
        assertEquals(5, passing("POST", "/reports", "10.0.0.13", 5));
    }

    @Test
    void theCallbackReplayOverGetDrainsTheSameAllowanceAsTheProviderPost() throws Exception {
        properties.getWrite().setMaxAttempts(3);

        assertEquals(2, passing("POST", "/payments/callback", "10.0.0.15", 2));
        assertEquals(1, passing("GET", "/payments/callback", "10.0.0.15", 3),
                "GET /payments/callback POST ile ayni WRITE kovasindan saymali (#252)");
        assertEquals(200, passing("GET", "/payments/other", "10.0.0.15", 200),
                "diger GET'ler sinirsiz kalmali");
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
        assertEquals(2, passing("POST", "/public/contact", "10.0.0.6", 5));
    }

    @Test
    void loginTargetsBehindOneIpDoNotLockEachOtherOut() throws Exception {
        properties.setMaxAttempts(1);

        assertEquals(1, passingLogin("first@example.com", 2));
        assertEquals(1, passingLogin("second@example.com", 1));
    }

    @Test
    void identityVerificationTargetsBehindOneIpDoNotLockEachOtherOut() throws Exception {
        properties.setMaxAttempts(1);

        assertEquals(1, passingAuth("/users/me/identity-verification",
                "{\"tcIdentityNumber\":\"11111111110\"}", 2));
        assertEquals(1, passingAuth("/users/me/identity-verification",
                "{\"tcIdentityNumber\":\"22222222220\"}", 1));
    }

    @Test
    void oversizedChunkedAuthBodiesAreRejectedBeforeTheRequestIsBuffered() throws Exception {
        byte[] body = new byte[128 * 1024];
        Arrays.fill(body, (byte) 'a');
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login") {
            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setRemoteAddr("10.0.0.12");
        request.setContentType("application/json");
        request.setContent(body);
        FilterChain call = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, call);

        assertEquals(413, response.getStatus());
        verify(call, never()).doFilter(any(), any());
    }

    @Test
    void twoClientsDoNotShareAnAllowance() throws Exception {
        properties.getWrite().setMaxAttempts(2);

        assertEquals(2, passing("POST", "/reports", "10.0.0.7", 5));
        assertEquals(2, passing("POST", "/reports", "10.0.0.8", 5));
    }

    private int passingLogin(String email, int attempts) throws Exception {
        return passingAuth("/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"secret\"}", attempts);
    }

    private int passingAuth(String uri, String json, int attempts) throws Exception {
        int passed = 0;
        for (int i = 0; i < attempts; i++) {
            FilterChain call = mock(FilterChain.class);
            MockHttpServletRequest request = request("POST", uri, "10.0.0.12");
            request.setContentType("application/json");
            request.setContent(json.getBytes());
            filter.doFilter(request, new MockHttpServletResponse(), call);
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
    void theStaffLoginEndpointSharesTheAuthBudget() throws Exception {
        assertEquals(properties.getAuth().getMaxAttempts(),
                passing("POST", "/admin/auth/login", "10.0.0.90", 40),
                "personel girişi §2j'deki AUTH kovasına bağlı olmalı");
    }

    @Test
    void theStaffLoginBudgetIsNotTheGenericWriteBudget() throws Exception {
        assertEquals(properties.getWrite().getMaxAttempts(),
                passing("POST", "/admin/staff", "10.0.0.91", 200),
                "personel yönetimi yazması WRITE kovasında kalmalı");
    }

    @Test
    void theStaffRefreshAndPasswordEndpointsAreAuthToo() throws Exception {
        assertEquals(properties.getAuth().getMaxAttempts(),
                passing("POST", "/admin/auth/refresh", "10.0.0.92", 40));
        assertEquals(properties.getAuth().getMaxAttempts(),
                passing("POST", "/admin/auth/password", "10.0.0.93", 40));
    }
}
