package com.exskylab.koala.core.helpers;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.regex.Pattern;

public final class RequestResourceResolver {

    private static final Pattern IDENTIFIER = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$|^\\d+$");

    private RequestResourceResolver() {
    }

    public record Target(String resource, String id) {

        static final Target EMPTY = new Target(null, null);
    }

    public static Target resolve() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return resolve(attributes.getRequest().getRequestURI());
        }
        return Target.EMPTY;
    }

    public static Target resolve(String uri) {
        if (uri == null || uri.isBlank()) {
            return Target.EMPTY;
        }

        String[] segments = uri.split("/");
        String resource = null;
        String identifier = null;

        for (String segment : segments) {
            if (segment.isBlank()) {
                continue;
            }

            if (resource != null && IDENTIFIER.matcher(segment).matches()) {
                identifier = segment;
                continue;
            }

            if (!IDENTIFIER.matcher(segment).matches()) {
                resource = segment;
            }
        }

        return new Target(resource, identifier);
    }
}
