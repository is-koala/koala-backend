package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.constants.AuthMessages;
import com.exskylab.koala.core.exceptions.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;

public final class BearerTokenExtractor {

    private static final String PREFIX = "Bearer ";

    private BearerTokenExtractor() {
    }

    public static String extract(HttpServletRequest request) {
        return extractHeader(request.getHeader("Authorization"));
    }

    public static String extractHeader(String header) {
        if (header == null || !header.startsWith(PREFIX)) {
            throw new UnauthorizedException(AuthMessages.NOT_VALID_AUTHORIZATION_HEADER);
        }

        String token = header.substring(PREFIX.length()).trim();

        if (token.isEmpty()) {
            throw new UnauthorizedException(AuthMessages.NOT_VALID_AUTHORIZATION_HEADER);
        }

        return token;
    }
}
