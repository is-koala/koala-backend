package com.exskylab.koala.core.configs;

import com.exskylab.koala.business.abstracts.SettingsService;
import com.exskylab.koala.core.helpers.AppVersion;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MinimumAppVersionTest {

    private SettingsService settings;
    private MinimumAppVersionFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        settings = mock(SettingsService.class);
        when(settings.stringValue(anyString(), anyString())).thenReturn("1.4.0");
        filter = new MinimumAppVersionFilter(settings, "");
        chain = mock(FilterChain.class);
    }

    private MockHttpServletResponse run(String path, String version) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (version != null) {
            request.addHeader("X-App-Version", version);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void aCurrentClientPassesThrough() throws Exception {
        assertEquals(200, run("/jobs", "1.4.0").getStatus());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void aNewerClientPassesThrough() throws Exception {
        assertEquals(200, run("/jobs", "2.0.1").getStatus());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void anOutdatedClientIsRefusedWithUpgradeRequired() throws Exception {
        MockHttpServletResponse response = run("/jobs", "1.3.9");

        assertEquals(426, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void theRefusalCarriesTheRequiredVersionSoTheClientCanExplainItself() throws Exception {
        MockHttpServletResponse response = run("/jobs", "1.0.0");

        assertEquals("1.4.0", response.getHeader("X-Minimum-App-Version"));
        assertTrue(response.getContentAsString().contains("UPGRADE_REQUIRED"));
    }

    @Test
    void aRequestWithoutTheHeaderIsNotBlocked() throws Exception {
        assertEquals(200, run("/jobs", null).getStatus());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void anUnparsableVersionIsNotBlocked() throws Exception {
        assertEquals(200, run("/jobs", "surum-yok").getStatus());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void healthChecksAreNeverBlocked() throws Exception {
        assertEquals(200, run("/actuator/health/readiness", "0.0.1").getStatus());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void theProviderCallbackIsNeverBlocked() throws Exception {
        assertEquals(200, run("/payments/callback", "0.0.1").getStatus());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void theConfiguredValueIsTheFallbackWhenNoSettingRowExists() throws Exception {
        SettingsService empty = mock(SettingsService.class);
        when(empty.stringValue(anyString(), anyString())).thenAnswer(i -> i.getArgument(1));
        filter = new MinimumAppVersionFilter(empty, "2.0.0");

        assertEquals(426, run("/jobs", "1.9.0").getStatus());
    }

    @Test
    void anEmptyThresholdDisablesTheGate() throws Exception {
        when(settings.stringValue(anyString(), anyString())).thenReturn("");

        assertEquals(200, run("/jobs", "0.0.1").getStatus());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void versionsAreComparedNumericallyNotAlphabetically() {
        assertTrue(AppVersion.isAtLeast("1.10.0", "1.9.0"));
        assertTrue(AppVersion.isAtLeast("2.0.0", "1.99.99"));
        assertFalse(AppVersion.isAtLeast("1.9.0", "1.10.0"));
        assertTrue(AppVersion.isAtLeast("1.4", "1.4.0"));
        assertTrue(AppVersion.isAtLeast("1.4.0+build7", "1.4.0"));
    }
}
