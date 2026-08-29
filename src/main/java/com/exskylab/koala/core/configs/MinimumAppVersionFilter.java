package com.exskylab.koala.core.configs;

import com.exskylab.koala.business.abstracts.SettingsService;
import com.exskylab.koala.core.constants.SettingKeys;
import com.exskylab.koala.core.helpers.AppVersion;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
@Slf4j
public class MinimumAppVersionFilter extends OncePerRequestFilter {

    private static final String VERSION_HEADER = "X-App-Version";
    private static final String MINIMUM_HEADER = "X-Minimum-App-Version";

    private static final List<String> ALWAYS_ALLOWED = List.of(
            "/actuator/", "/payments/callback", "/uploads/", "/notification-tracking/",
            "/swagger-ui", "/v3/api-docs");

    private final SettingsService settingsService;
    private final String configuredMinimum;

    public MinimumAppVersionFilter(SettingsService settingsService,
                                   @Value("${app.minimum-version:}") String configuredMinimum) {
        this.settingsService = settingsService;
        this.configuredMinimum = configuredMinimum == null ? "" : configuredMinimum;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String minimum = settingsService.stringValue(SettingKeys.MINIMUM_APP_VERSION, configuredMinimum);
        String claimed = request.getHeader(VERSION_HEADER);

        if (minimum.isBlank() || claimed == null || isAlwaysAllowed(request.getRequestURI())
                || !AppVersion.isParsable(claimed) || AppVersion.isAtLeast(claimed, minimum)) {
            chain.doFilter(request, response);
            return;
        }

        log.warn("Client version {} is below the required {} for {} {}",
                claimed, minimum, request.getMethod(), request.getRequestURI());

        response.setStatus(HttpServletResponse.SC_UPGRADE_REQUIRED);
        response.setHeader(MINIMUM_HEADER, minimum);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"success":false,"httpStatus":"UPGRADE_REQUIRED","errorCategory":"CLIENT_TOO_OLD",\
                "errorCode":"UPGRADE_REQUIRED","message":"%s","path":"%s","timeStamp":"%s"}"""
                .formatted("Lutfen uygulamayi guncelleyin.", request.getRequestURI(), LocalDateTime.now()));
    }

    private static boolean isAlwaysAllowed(String path) {
        return ALWAYS_ALLOWED.stream().anyMatch(path::startsWith);
    }
}
