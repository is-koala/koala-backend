package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.properties.CorsProperties;
import com.exskylab.koala.core.properties.RealtimeProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Component
public class CorsConfig {

    private static final String PAYMENT_CALLBACK_PATH = "/payments/callback";

    private final CorsProperties corsProperties;
    private final RealtimeProperties realtimeProperties;

    public CorsConfig(CorsProperties corsProperties, RealtimeProperties realtimeProperties) {
        this.corsProperties = corsProperties;
        this.realtimeProperties = realtimeProperties;
    }

    @Bean
    public CorsFilter corsFilter(){
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(corsProperties.getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Origin", "Content-Type", "Accept", "Authorization"));

        config.setExposedHeaders(List.of("X-Request-Id", "Authorization"));

        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(request ->
                isRealtimeHandshake(request) || isPaymentCallback(request)
                        ? null
                        : source.getCorsConfiguration(request));
    }

    private boolean isPaymentCallback(HttpServletRequest request) {
        return PAYMENT_CALLBACK_PATH.equals(request.getRequestURI());
    }

    private boolean isRealtimeHandshake(HttpServletRequest request) {
        String endpoint = realtimeProperties.getEndpoint();
        String path = request.getRequestURI();

        return path != null && (path.equals(endpoint) || path.startsWith(endpoint + "/"));
    }
}
