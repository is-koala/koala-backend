package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.security.SessionAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
                        .requestMatchers(HttpMethod.POST,"/auth/logout").authenticated()

                        //companycontactinvitation endpoints
                        .requestMatchers(HttpMethod.POST,"/company-contact-invitations/{invitationId}").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/company-contact-invitations/{invitationId}").hasAnyRole("USER", "ADMIN")

                        //company endpoints
                        .requestMatchers(HttpMethod.POST,"/companies").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/companies/{companyId}/contact-invitations").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/companies/{companyId}/contact-invitations").hasAnyRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/companies/{companyId}/jobs").hasAnyRole("USER", "ADMIN")

                        //image endpoints
                        .requestMatchers(HttpMethod.POST,"/images").hasAnyRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE,"/images/{imageId}").hasAnyRole("USER", "ADMIN")

                        //notification endpoints
                        .requestMatchers(HttpMethod.GET,"/notifications/track/**").permitAll()

                        //user endpoints
                        .requestMatchers(HttpMethod.GET, "/users/me").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/users/me").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/users/me/password").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/users/me/profile-picture").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/users/me/identity-verification").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/users/me/jobs").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/users/me/address").hasAnyRole("USER", "ADMIN")
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
                        .requestMatchers(HttpMethod.POST, "/user-verifications/{id}").hasAnyRole("USER", "ADMIN")


                        //legal endpoints
                        .requestMatchers(HttpMethod.GET, "/legal/agreements").permitAll()
                        .requestMatchers(HttpMethod.GET, "/legal/consents/me").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/legal/consents").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/legal/account-deletion").hasAnyRole("USER", "ADMIN")

                        //reference data (public)
                        .requestMatchers(HttpMethod.GET, "/reference/**").permitAll()

                        //job endpoints
                        .requestMatchers(HttpMethod.GET, "/jobs").permitAll()
                        .requestMatchers(HttpMethod.GET, "/jobs/{jobId}").permitAll()
                        .requestMatchers(HttpMethod.POST, "/jobs/{jobId}/close").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users/me/jobs").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/jobs/{jobId}/applications").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/jobs/{jobId}/applications").hasAnyRole("USER", "ADMIN")


                        //job-application endpoints
                        .requestMatchers(HttpMethod.PATCH, "/job-applications/{applicationId}").hasAnyRole("USER", "ADMIN")



                        //job-assignment endpoints
                        .requestMatchers(HttpMethod.PATCH, "/job-assignments/{assignmentId}/status").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/job-assignments/{assignmentId}/payment-session/initiate").hasAnyRole("USER", "ADMIN")


                        //payment endpoints
                        .requestMatchers(HttpMethod.POST, "/payments/callback").permitAll()
                        .requestMatchers(HttpMethod.GET, "/payments/job-assignments/{assignmentId}").hasAnyRole("USER", "ADMIN")

                        //payout endpoints
                        .requestMatchers(HttpMethod.GET, "/payouts/me").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/payouts/job-assignments/{assignmentId}/release").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/payouts/job-assignments/{assignmentId}/refund").hasRole("ADMIN")

                        //messaging endpoints
                        .requestMatchers("/conversations/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/users/me/blocks/**").hasAnyRole("USER", "ADMIN")

                        //assignment operation endpoints
                        .requestMatchers(HttpMethod.POST, "/job-assignments/{assignmentId}/cancel").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/job-assignments/{assignmentId}/cancellation").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/job-assignments/{assignmentId}/disputes").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/job-assignments/{assignmentId}/disputes").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/job-assignments/{assignmentId}/check-in").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/job-assignments/{assignmentId}/check-out").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/job-assignments/{assignmentId}/work-sessions").hasAnyRole("USER", "ADMIN")

                        //dispute administration endpoints
                        .requestMatchers(HttpMethod.GET, "/disputes/pending").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/disputes/{disputeId}/resolve").hasRole("ADMIN")

                        //report endpoints
                        .requestMatchers(HttpMethod.POST, "/reports").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/reports/me").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/reports").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/reports/{reportId}/review").hasRole("ADMIN")

                        //review endpoints
                        .requestMatchers(HttpMethod.POST, "/reviews/job-assignments/{assignmentId}").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/reviews/users/{userId}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/reviews/users/{userId}/summary").permitAll()

                        //actuator
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")

                        //swagger
                        .requestMatchers("/swagger-ui/**").permitAll()
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
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }


}
