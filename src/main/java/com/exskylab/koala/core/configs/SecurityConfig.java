package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.security.SessionAuthFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final SessionAuthFilter sessionAuthFilter;

    private final SecurityExceptionConfig securityExceptionConfig;

    public SecurityConfig(SessionAuthFilter sessionAuthFilter,
                          SecurityExceptionConfig securityExceptionConfig) {
        this.sessionAuthFilter = sessionAuthFilter;
        this.securityExceptionConfig = securityExceptionConfig;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
                .csrf(AbstractHttpConfigurer::disable)

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityExceptionConfig)
                        .accessDeniedHandler(securityExceptionConfig))

                .authorizeHttpRequests(auth -> auth
                        //auth endpoints
                        .requestMatchers(HttpMethod.POST, "/auth/start-registration").permitAll()
                        .requestMatchers(HttpMethod.POST,"/auth/verify-registration-token").permitAll()
                        .requestMatchers(HttpMethod.POST,"/auth/complete-registration").permitAll()
                        .requestMatchers(HttpMethod.POST,"/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST,"/auth/refresh-token").permitAll()
                        .requestMatchers(HttpMethod.POST,"/auth/forgot-password").permitAll()
                        .requestMatchers(HttpMethod.POST,"/auth/reset-password").permitAll()
                        .requestMatchers(HttpMethod.GET,"/auth-links/**").permitAll()
                        .requestMatchers(HttpMethod.POST,"/auth/logout").authenticated()

                        //companycontactinvitation endpoints
                        .requestMatchers(HttpMethod.POST,"/contact-invitations/{invitationId}").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/contact-invitations/{invitationId}").hasAnyRole("USER", "ADMIN")

                        //company endpoints
                        .requestMatchers(HttpMethod.POST,"/companies").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/companies/{companyId}/contact-invitations").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/companies/{companyId}/contact-invitations").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/companies/{companyId}/jobs").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/companies/{companyId}/jobs").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/companies/{companyId}").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/companies/{companyId}").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/companies/{companyId}/contacts").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/companies/{companyId}/contacts/{userId}").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/companies/{companyId}/logo").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/companies/{companyId}/logo").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/companies/{companyId}/follow").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/companies/{companyId}/follow").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/companies/{companyId}/images").permitAll()
                        .requestMatchers(HttpMethod.POST, "/companies/{companyId}/images").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/companies/{companyId}/images").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/companies/{companyId}/images/{imageId}").hasAnyRole("USER", "ADMIN")


                        //image endpoints
                        .requestMatchers(HttpMethod.POST,"/images").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE,"/images/{imageId}").hasAnyRole("USER", "ADMIN")

                        //notification endpoints
                        .requestMatchers(HttpMethod.GET,"/notification-tracking/**").permitAll()

                        //user endpoints
                        .requestMatchers(HttpMethod.GET, "/users/me").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/users/me").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/users/me/password").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/users/me/profile-picture").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/users/me/identity-verification").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/users/me/identity-verification/challenge").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/users/me/identity-documents").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users/me/identity-documents").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/users/me/jobs").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/users/me/address").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users/me/applications").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users/me/assignments").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users/me/sessions").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/users/me/sessions").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/users/me/sessions/{sessionId}").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users/me/notifications").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/users/me/notifications/read").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/users/me/notifications/{notificationId}/read").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users/me/notification-preferences").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/users/me/notification-preferences").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users/me/bank-accounts").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/users/me/bank-accounts").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/users/me/bank-accounts/{accountId}/primary").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/users/me/bank-accounts/{accountId}/verification").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/users/me/bank-accounts/{accountId}").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/notification-preferences/unsubscribe").permitAll()
                        .requestMatchers(HttpMethod.GET, "/users/me/work").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/users/me/devices/push-token").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/users/me/devices/push-token").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users/me/followed-companies").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users/{userId}").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users/{userId}/employer-stats").permitAll()
                        .requestMatchers(HttpMethod.GET, "/users/me/saved-jobs").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/users/me/saved-jobs/{jobId}").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/users/me/saved-jobs/{jobId}").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/users/me/cv").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/users/me/cv").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users/me/educations").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/users/me/educations").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/users/me/educations/{educationId}").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/users/me/educations/{educationId}").hasAnyRole("USER", "ADMIN")

                        //verification endpoints
                        .requestMatchers(HttpMethod.POST, "/verifications/{verificationId}/confirm").hasAnyRole("USER", "ADMIN")


                        //legal endpoints
                        .requestMatchers(HttpMethod.POST, "/public/contact").permitAll()
                        .requestMatchers(HttpMethod.GET, "/legal/agreements").permitAll()
                        .requestMatchers(HttpMethod.GET, "/legal/consents/me").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/legal/consents").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/legal/account-deletion").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/legal/account-deletion").hasAnyRole("USER", "ADMIN")

                        //reference data (public)
                        .requestMatchers(HttpMethod.GET, "/reference/**").permitAll()

                        .requestMatchers("/locations/address-search-sessions/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/locations/address-candidates/reverse").hasAnyRole("USER", "ADMIN")

                        //job endpoints
                        .requestMatchers(HttpMethod.GET, "/jobs").permitAll()
                        .requestMatchers(HttpMethod.GET, "/jobs/{jobId}").permitAll()
                        .requestMatchers(HttpMethod.POST, "/jobs/{jobId}/close").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/jobs/{jobId}/cancel").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/jobs/{jobId}/images").permitAll()
                        .requestMatchers(HttpMethod.POST, "/jobs/{jobId}/images").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/jobs/{jobId}/images").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/jobs/{jobId}/images/{imageId}").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users/me/jobs").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/jobs/{jobId}/applications").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/jobs/{jobId}/applications").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/sitemap/jobs/**").permitAll()


                        //job-application endpoints
                        .requestMatchers(HttpMethod.POST, "/job-applications/{applicationId}/respond").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/job-applications/{applicationId}/withdraw").hasAnyRole("USER", "ADMIN")



                        //job-assignment endpoints
                        .requestMatchers(HttpMethod.GET, "/job-assignments/{assignmentId}").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/job-assignments/{assignmentId}/respond").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/job-assignments/{assignmentId}/payment-session").hasAnyRole("USER", "ADMIN")


                        //payment endpoints
                        .requestMatchers(HttpMethod.POST, "/payment-quotes").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/payments/callback").permitAll()
                        .requestMatchers(HttpMethod.GET, "/job-assignments/{assignmentId}/payments").hasAnyRole("USER", "ADMIN")

                        //payout endpoints
                        .requestMatchers(HttpMethod.GET, "/users/me/payouts").hasAnyRole("USER", "ADMIN")

                        //worker profile endpoints
                        .requestMatchers(HttpMethod.GET, "/users/{userId}/experiences").permitAll()
                        .requestMatchers(HttpMethod.GET, "/users/{userId}/availability").permitAll()
                        .requestMatchers("/users/me/experiences/**", "/users/me/experiences").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/users/me/availability/**", "/users/me/availability").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/users/me/job-alerts/**", "/users/me/job-alerts").hasAnyRole("USER", "ADMIN")

                        //employer tool endpoints
                        .requestMatchers("/users/me/talent-pool/**", "/users/me/talent-pool").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/users/me/job-templates/**", "/users/me/job-templates").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users/me/received-applications").hasAnyRole("USER", "ADMIN")

                        //storage endpoints
                        .requestMatchers(HttpMethod.GET, "/uploads/images/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/uploads/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/uploads/files/**").hasAnyRole("USER", "ADMIN")

                        //realtime endpoints
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/users/me/realtime-credential").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users/{userId}/presence").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/presence/lookup").hasAnyRole("USER", "ADMIN")

                        //messaging endpoints
                        .requestMatchers("/conversations/**", "/jobs/{jobId}/conversation").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/users/me/blocks/**").hasAnyRole("USER", "ADMIN")

                        //assignment operation endpoints
                        .requestMatchers(HttpMethod.POST, "/job-assignments/{assignmentId}/complete").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/job-assignments/{assignmentId}/cancel").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/job-assignments/{assignmentId}/cancellation").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/job-assignments/{assignmentId}/cancellation-preview").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/job-assignments/{assignmentId}/disputes").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/job-assignments/{assignmentId}/disputes").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/disputes/{disputeId}/evidence").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/disputes/{disputeId}/evidence").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/disputes/{disputeId}/evidence/{evidenceId}/download").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/job-assignments/{assignmentId}/check-in").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/job-assignments/{assignmentId}/check-out").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/job-assignments/{assignmentId}/work-sessions").hasAnyRole("USER", "ADMIN")

                        //dispute administration endpoints

                        //report endpoints
                        .requestMatchers(HttpMethod.POST, "/reports").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/reports/me").hasAnyRole("USER", "ADMIN")

                        //badge endpoints
                        .requestMatchers(HttpMethod.GET, "/users/{userId}/badges").permitAll()

                        //review endpoints
                        .requestMatchers(HttpMethod.POST, "/job-assignments/{assignmentId}/reviews").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users/{userId}/reviews").permitAll()
                        .requestMatchers(HttpMethod.GET, "/users/{userId}/rating-summary").permitAll()

                        //actuator
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")

                        //swagger
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/v3/api-docs.yaml").permitAll()

                        //allow preflight requests
                        .requestMatchers(HttpMethod.OPTIONS).permitAll()



                        .anyRequest().authenticated()
                )

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .addFilterBefore(sessionAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }



    @Bean
    public FilterRegistrationBean<SessionAuthFilter> sessionAuthFilterRegistration() {
        FilterRegistrationBean<SessionAuthFilter> registration = new FilterRegistrationBean<>(sessionAuthFilter);
        registration.setEnabled(false);
        return registration;
    }


    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }


}
