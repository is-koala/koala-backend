package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.constants.AuthMessages;
import com.exskylab.koala.core.exceptions.NotValidAuthorizationHeaderException;
import jakarta.servlet.http.HttpServletRequest;

public final class BearerTokenExtractor {

    private static final String PREFIX = "Bearer ";

    private BearerTokenExtractor() {
    }

    public static String extract(HttpServletRequest request) {
        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith(PREFIX)) {
            throw new NotValidAuthorizationHeaderException(AuthMessages.NOT_VALID_AUTHORIZATION_HEADER);
        }

        String token = header.substring(PREFIX.length()).trim();

        if (token.isEmpty()) {
            throw new NotValidAuthorizationHeaderException(AuthMessages.NOT_VALID_AUTHORIZATION_HEADER);
        }

        return token;
    }
}
