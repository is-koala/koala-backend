package com.exskylab.koala.core.configs;

import com.exskylab.koala.business.abstracts.StaffAuditService;
import com.exskylab.koala.core.properties.StaffTokenProperties;
import com.exskylab.koala.core.security.SessionAuthFilter;
import com.exskylab.koala.core.security.StaffAuditFilter;
import com.exskylab.koala.core.security.StaffAuthenticationManagerResolver;
import com.exskylab.koala.core.security.StaffBearerTokenResolver;
import com.exskylab.koala.core.security.StaffPermissionAuthorizationManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class StaffSecurityConfig {

    private final SessionAuthFilter sessionAuthFilter;
    private final SecurityExceptionConfig securityExceptionConfig;
    private final StaffPermissionAuthorizationManager staffPermissionAuthorizationManager;
    private final StaffBearerTokenResolver staffBearerTokenResolver;
    private final StaffAuthenticationManagerResolver staffAuthenticationManagerResolver;
    private final StaffTokenProperties staffTokenProperties;
    private final StaffAuditService staffAuditService;

    public StaffSecurityConfig(SessionAuthFilter sessionAuthFilter,
                               SecurityExceptionConfig securityExceptionConfig,
                               StaffPermissionAuthorizationManager staffPermissionAuthorizationManager,
                               StaffBearerTokenResolver staffBearerTokenResolver,
                               StaffAuthenticationManagerResolver staffAuthenticationManagerResolver,
                               StaffTokenProperties staffTokenProperties,
                               StaffAuditService staffAuditService) {
        this.sessionAuthFilter = sessionAuthFilter;
        this.securityExceptionConfig = securityExceptionConfig;
        this.staffPermissionAuthorizationManager = staffPermissionAuthorizationManager;
        this.staffBearerTokenResolver = staffBearerTokenResolver;
        this.staffAuthenticationManagerResolver = staffAuthenticationManagerResolver;
        this.staffTokenProperties = staffTokenProperties;
        this.staffAuditService = staffAuditService;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain staffFilterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .securityMatcher("/admin/**")

                .csrf(AbstractHttpConfigurer::disable)

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityExceptionConfig)
                        .accessDeniedHandler(securityExceptionConfig))

                .authorizeHttpRequests(auth -> auth
                        .anyRequest().access(staffPermissionAuthorizationManager))

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .addFilterAfter(sessionAuthFilter, BearerTokenAuthenticationFilter.class)

                .addFilterAfter(new StaffAuditFilter(staffAuditService), SessionAuthFilter.class);

        if (staffTokenProperties.isConfigured()) {
            httpSecurity.oauth2ResourceServer(resourceServer -> resourceServer
                    .bearerTokenResolver(staffBearerTokenResolver)
                    .authenticationEntryPoint(securityExceptionConfig)
                    .accessDeniedHandler(securityExceptionConfig)
                    .authenticationManagerResolver(staffAuthenticationManagerResolver));
        }

        return httpSecurity.build();
    }
}
