package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.security.SessionAuthFilter;
import com.exskylab.koala.support.AbstractIntegrationTest;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionAuthFilterRegistrationIT extends AbstractIntegrationTest {

    @Autowired
    private List<FilterRegistrationBean<?>> filterRegistrations;

    @Autowired
    private List<SecurityFilterChain> securityFilterChains;

    @Autowired
    private SessionAuthFilter sessionAuthFilter;

    @Test
    void sessionAuthFilterIsNotRegisteredWithTheServletContainer() {
        List<FilterRegistrationBean<?>> forSessionAuthFilter = filterRegistrations.stream()
                .filter(registration -> {
                    Filter registered = registration.getFilter();
                    return registered == sessionAuthFilter;
                })
                .toList();

        assertEquals(1, forSessionAuthFilter.size(),
                "SessionAuthFilter için tam olarak bir konteyner kaydı beklenir");
        assertFalse(forSessionAuthFilter.getFirst().isEnabled(),
                "SessionAuthFilter servlet konteynerine kaydedilmemeli, yalnız güvenlik zincirinin üyesi olmalı");
    }

    @Test
    void sessionAuthFilterStaysInsideTheSecurityFilterChain() {
        List<Filter> filters = securityFilterChains.stream()
                .flatMap(chain -> chain.getFilters().stream())
                .toList();

        assertTrue(filters.contains(sessionAuthFilter),
                "SessionAuthFilter güvenlik zincirinin üyesi kalmalı");
    }
}
