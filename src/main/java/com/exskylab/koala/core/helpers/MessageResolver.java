package com.exskylab.koala.core.helpers;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MessageResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile("^\\{([a-zA-Z0-9._-]+)}$");

    private final MessageSource messageSource;

    public MessageResolver(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String resolve(String candidate) {
        return resolve(candidate, LocaleContextHolder.getLocale());
    }

    public String resolve(String candidate, Locale locale) {
        if (candidate == null || candidate.isBlank()) {
            return candidate;
        }

        Matcher matcher = PLACEHOLDER.matcher(candidate.trim());
        if (!matcher.matches()) {
            return candidate;
        }

        String key = matcher.group(1);
        return messageSource.getMessage(key, null, key, locale);
    }
}
