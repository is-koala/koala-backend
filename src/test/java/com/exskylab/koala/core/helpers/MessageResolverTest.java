package com.exskylab.koala.core.helpers;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MessageResolverTest {

    private final MessageResolver resolver = new MessageResolver(messageSource());

    private StaticMessageSource messageSource() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("user.not.found", Locale.of("tr"), "Kullanıcı bulunamadı.");
        source.addMessage("user.not.found", Locale.ENGLISH, "User not found.");
        return source;
    }

    @Test
    void aKnownPlaceholderIsReplacedByItsMessage() {
        assertEquals("Kullanıcı bulunamadı.", resolver.resolve("{user.not.found}", Locale.of("tr")));
        assertEquals("User not found.", resolver.resolve("{user.not.found}", Locale.ENGLISH));
    }

    @Test
    void anUnknownPlaceholderFallsBackToItsKey() {
        assertEquals("some.missing.key", resolver.resolve("{some.missing.key}", Locale.ENGLISH));
    }

    @Test
    void plainTextPassesThroughUntouched() {
        assertEquals("Duz metin", resolver.resolve("Duz metin", Locale.ENGLISH));
        assertEquals("50 {} karakter", resolver.resolve("50 {} karakter", Locale.ENGLISH));
    }

    @Test
    void nullAndBlankAreLeftAlone() {
        assertNull(resolver.resolve(null, Locale.ENGLISH));
        assertEquals("", resolver.resolve("", Locale.ENGLISH));
        assertEquals("   ", resolver.resolve("   ", Locale.ENGLISH));
    }

    @Test
    void surroundingWhitespaceDoesNotBreakResolution() {
        assertEquals("User not found.", resolver.resolve("  {user.not.found}  ", Locale.ENGLISH));
    }
}
