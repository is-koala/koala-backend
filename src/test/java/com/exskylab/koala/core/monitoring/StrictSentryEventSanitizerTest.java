package com.exskylab.koala.core.monitoring;

import io.sentry.Breadcrumb;
import io.sentry.SentryEvent;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrictSentryEventSanitizerTest {

    @Test
    void everyUserControlledPayloadIsRemovedBeforeTransmission() {
        SentryEvent event = new SentryEvent();
        Request request = new Request();
        request.setUrl("https://api.example.test/users/token-value");
        request.setHeaders(Map.of("Authorization", "Bearer token-value"));
        request.setData(Map.of("email", "person@example.test"));
        event.setRequest(request);

        User user = new User();
        user.setEmail("person@example.test");
        user.setIpAddress("198.51.100.8");
        event.setUser(user);
        event.setExtras(Map.of("phone", "05555555555"));
        event.setTags(Map.of("safe.code", "PAYMENT_CALLBACK_FAILED", "email", "person@example.test"));
        event.setBreadcrumbs(List.of(new Breadcrumb("address: Example Street 12")));
        event.getContexts().put("payment", Map.of("providerToken", "token-value"));

        SentryException exception = new SentryException();
        exception.setType("IllegalStateException");
        exception.setValue("person@example.test failed with token-value");
        event.setExceptions(List.of(exception));
        event.setThrowable(new IllegalStateException("person@example.test"));

        SentryEvent sanitized = new StrictSentryEventSanitizer().execute(event, null);

        assertNull(sanitized.getRequest());
        assertNull(sanitized.getUser());
        assertNull(sanitized.getThrowable());
        assertTrue(sanitized.getExtras().isEmpty());
        assertTrue(sanitized.getBreadcrumbs().isEmpty());
        assertTrue(sanitized.getContexts().isEmpty());
        assertEquals(Map.of("safe.code", "PAYMENT_CALLBACK_FAILED"), sanitized.getTags());
        assertEquals("[redacted]", sanitized.getExceptions().getFirst().getValue());
    }
}
