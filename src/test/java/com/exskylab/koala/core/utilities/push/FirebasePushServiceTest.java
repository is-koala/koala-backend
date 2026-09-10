package com.exskylab.koala.core.utilities.push;

import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Message;
import com.google.api.client.json.gson.GsonFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.time.Duration;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FirebasePushServiceTest {

    @Test
    void sendsATokenTargetedMessageAndReturnsTheProviderId() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        when(messaging.send(any())).thenReturn("projects/p/messages/m");
        FirebasePushService service = new FirebasePushService(messaging);

        assertEquals("projects/p/messages/m", service.send(
                new PushMessage("token", "title", "body", Map.of("notificationId", "id")),
                Duration.ofSeconds(45)));

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        org.mockito.Mockito.verify(messaging).send(message.capture());
        String json = GsonFactory.getDefaultInstance().toString(message.getValue());
        assertTrue(json.contains("\"token\":\"token\""));
        assertTrue(json.contains("\"title\":\"title\""));
        assertTrue(json.contains("\"body\":\"body\""));
        assertTrue(json.contains("\"notificationId\":\"id\""));
        assertFalse(json.contains("\"topic\""));
        assertFalse(json.contains("\"fid\""));
    }

    @Test
    void malformedLocalPayloadBecomesASanitizedPermanentFailure() {
        FirebasePushService service = new FirebasePushService(mock(FirebaseMessaging.class));

        PushDeliveryException exception = assertThrows(PushDeliveryException.class,
                () -> service.send(new PushMessage(" ", "title", "secret-body", Map.of()),
                        Duration.ofSeconds(45)));

        assertEquals(PushFailureCode.INVALID_MESSAGE, exception.code());
        assertEquals("INVALID_MESSAGE", exception.getMessage());
        assertFalse(exception.retryable());
    }

    @Test
    void disabledProviderFailsPermanentlyWithoutReadingCredentials() {
        PushDeliveryException exception = assertThrows(PushDeliveryException.class,
                () -> new UnconfiguredPushService().send(
                        new PushMessage("token", "title", "body", Map.of()),
                        Duration.ofSeconds(45)));

        assertEquals(PushFailureCode.PROVIDER_NOT_CONFIGURED, exception.code());
        assertFalse(exception.retryable());
    }

    @Test
    void configuredAndUnconfiguredProvidersRejectAMissingDeadline() {
        FirebasePushService firebase = new FirebasePushService(mock(FirebaseMessaging.class));
        PushMessage message = new PushMessage("token", "title", "body", Map.of());

        assertThrows(IllegalArgumentException.class, () -> firebase.send(message, null));
        assertThrows(IllegalArgumentException.class, () -> firebase.send(message, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new UnconfiguredPushService().send(message, null));
        assertThrows(IllegalArgumentException.class,
                () -> new UnconfiguredPushService().send(message, Duration.ZERO));
    }

    @Test
    void classifierClearsServiceLevelInvalidTokensButNotTopLevelInvalidPayloads() {
        FirebasePushService service = new FirebasePushService(mock(FirebaseMessaging.class));

        PushDeliveryException unregistered = service.classify(MessagingErrorCode.UNREGISTERED,
                ErrorCode.NOT_FOUND, 404, null, new RuntimeException("secret token"));
        PushDeliveryException invalidToken = service.classify(MessagingErrorCode.INVALID_ARGUMENT,
                ErrorCode.INVALID_ARGUMENT, 400, null, new RuntimeException("secret payload"));
        PushDeliveryException invalidPayload = service.classify(null,
                ErrorCode.INVALID_ARGUMENT, 400, null, new RuntimeException("secret payload"));

        assertEquals(PushFailureCode.INVALID_TOKEN, unregistered.code());
        assertEquals("INVALID_TOKEN", unregistered.getMessage());
        assertEquals(PushFailureCode.INVALID_TOKEN, invalidToken.code());
        assertTrue(invalidToken.invalidToken());
        assertEquals(PushFailureCode.INVALID_MESSAGE, invalidPayload.code());
        assertFalse(invalidPayload.invalidToken());
    }

    @Test
    void locallyInvalidDataCanNeverReachTheAmbiguousProviderResponse() {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        FirebasePushService service = new FirebasePushService(messaging);

        PushDeliveryException reservedKey = assertThrows(PushDeliveryException.class,
                () -> service.send(new PushMessage("token", "title", "body", Map.of("google.secret", "x")),
                        Duration.ofSeconds(45)));
        PushDeliveryException oversized = assertThrows(PushDeliveryException.class,
                () -> service.send(new PushMessage("token", "title", "x".repeat(4096), Map.of()),
                        Duration.ofSeconds(45)));

        assertEquals(PushFailureCode.INVALID_MESSAGE, reservedKey.code());
        assertEquals(PushFailureCode.INVALID_MESSAGE, oversized.code());
        org.mockito.Mockito.verifyNoInteractions(messaging);
    }

    @Test
    void senderMismatchWinsOverGenericPermissionDenied() {
        FirebasePushService service = new FirebasePushService(mock(FirebaseMessaging.class));

        PushDeliveryException failure = service.classify(MessagingErrorCode.SENDER_ID_MISMATCH,
                ErrorCode.PERMISSION_DENIED, 403, null, new RuntimeException("provider response"));

        assertEquals(PushFailureCode.SENDER_ID_MISMATCH, failure.code());
    }

    @Test
    void classifierPreservesBoundedRetryFactsWithoutProviderText() {
        FirebasePushService service = new FirebasePushService(mock(FirebaseMessaging.class));

        PushDeliveryException failure = service.classify(MessagingErrorCode.QUOTA_EXCEEDED,
                ErrorCode.RESOURCE_EXHAUSTED, 429, Duration.ofMinutes(7),
                new RuntimeException("response body and token"));

        assertEquals(PushFailureCode.RATE_LIMITED, failure.code());
        assertEquals(Duration.ofMinutes(7), failure.retryAfter());
        assertEquals("RATE_LIMITED", failure.getMessage());
        assertTrue(failure.retryable());
    }

    @Test
    void quotaRetryNeverSchedulesEarlierThanFirebaseMinimum() {
        FirebasePushService service = new FirebasePushService(mock(FirebaseMessaging.class));

        PushDeliveryException failure = service.classify(MessagingErrorCode.QUOTA_EXCEEDED,
                ErrorCode.RESOURCE_EXHAUSTED, 429, Duration.ofSeconds(5),
                new RuntimeException("response body and token"));

        assertEquals(Duration.ofMinutes(1), failure.retryAfter());
    }

    @Test
    void retryAfterParserAcceptsSecondsAndRfcDateButRejectsUnsafeValues() {
        FirebasePushService service = new FirebasePushService(mock(FirebaseMessaging.class));
        Instant now = Instant.parse("2026-09-09T12:00:00Z");

        assertEquals(Duration.ofMinutes(2), service.parseRetryAfter("120", now));
        assertEquals(Duration.ofMinutes(5),
                service.parseRetryAfter("Wed, 9 Sep 2026 12:05:00 GMT", now));
        assertEquals(null, service.parseRetryAfter("-1", now));
        assertEquals(null, service.parseRetryAfter("9223372036854775808", now));
        assertEquals(null, service.parseRetryAfter("not-a-date", now));
    }
}
