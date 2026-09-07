package com.exskylab.koala.core.configs;

import com.exskylab.koala.business.abstracts.StaffAuditService;
import com.exskylab.koala.core.security.SessionAuthFilter;
import com.exskylab.koala.core.security.StaffAuditFilter;
import com.exskylab.koala.core.security.StaffAuthFilter;
import com.exskylab.koala.core.security.StaffPermissionAuthorizationManager;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class StaffSecurityConfig {

    private final SessionAuthFilter sessionAuthFilter;
    private final StaffAuthFilter staffAuthFilter;
    private final SecurityExceptionConfig securityExceptionConfig;
    private final StaffPermissionAuthorizationManager staffPermissionAuthorizationManager;
    private final StaffAuditService staffAuditService;

    public StaffSecurityConfig(SessionAuthFilter sessionAuthFilter,
                               StaffAuthFilter staffAuthFilter,
                               SecurityExceptionConfig securityExceptionConfig,
                               StaffPermissionAuthorizationManager staffPermissionAuthorizationManager,
                               StaffAuditService staffAuditService) {
        this.sessionAuthFilter = sessionAuthFilter;
        this.staffAuthFilter = staffAuthFilter;
        this.securityExceptionConfig = securityExceptionConfig;
        this.staffPermissionAuthorizationManager = staffPermissionAuthorizationManager;
        this.staffAuditService = staffAuditService;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain staffFilterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
                .securityMatcher("/admin/**")

                .csrf(AbstractHttpConfigurer::disable)

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityExceptionConfig)
                        .accessDeniedHandler(securityExceptionConfig))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/admin/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/admin/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/admin/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.POST, "/admin/auth/password").authenticated()
                        .anyRequest().access(staffPermissionAuthorizationManager))

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .addFilterBefore(staffAuthFilter, UsernamePasswordAuthenticationFilter.class)

                .addFilterAfter(sessionAuthFilter, StaffAuthFilter.class)

                .addFilterAfter(new StaffAuditFilter(staffAuditService), SessionAuthFilter.class)

                .build();
    }

    @Bean
    public FilterRegistrationBean<StaffAuthFilter> staffAuthFilterRegistration() {
        FilterRegistrationBean<StaffAuthFilter> registration = new FilterRegistrationBean<>(staffAuthFilter);
        registration.setEnabled(false);
        return registration;
    }
}
