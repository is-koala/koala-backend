package com.exskylab.koala.core.utilities.push;

import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "push.firebase", name = "enabled", havingValue = "true")
public class FirebasePushService implements PushService {

    private static final int MAX_SERIALIZED_MESSAGE_BYTES = 4000;

    private final FirebaseMessaging firebaseMessaging;

    public FirebasePushService(FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    @Override
    @SuppressWarnings("deprecation")
    public String send(PushMessage pushMessage, Duration timeout) {
        requirePositiveTimeout(timeout);
        Message message;
        try {
            if (pushMessage == null || pushMessage.token() == null || pushMessage.token().isBlank()
                    || pushMessage.title() == null || pushMessage.body() == null) {
                throw new IllegalArgumentException();
            }
            Map<String, String> data = pushMessage.data() == null ? Map.of() : Map.copyOf(pushMessage.data());
            validateDataKeys(data);
            message = Message.builder()
                    .setToken(pushMessage.token())
                    .setNotification(com.google.firebase.messaging.Notification.builder()
                            .setTitle(pushMessage.title())
                            .setBody(pushMessage.body())
                            .build())
                    .putAllData(data)
                    .build();
            if (GsonFactory.getDefaultInstance().toByteArray(message).length > MAX_SERIALIZED_MESSAGE_BYTES) {
                throw new IllegalArgumentException();
            }
        } catch (IOException | IllegalArgumentException | NullPointerException exception) {
            throw PushDeliveryException.permanent(PushFailureCode.INVALID_MESSAGE, exception);
        }

        try {
            return firebaseMessaging.send(message);
        } catch (FirebaseMessagingException exception) {
            throw classify(exception);
        }
    }

    private PushDeliveryException classify(FirebaseMessagingException exception) {
        Integer statusCode = exception.getHttpResponse() == null
                ? null : exception.getHttpResponse().getStatusCode();
        return classify(exception.getMessagingErrorCode(), exception.getErrorCode(), statusCode,
                retryAfter(exception), exception);
    }

    PushDeliveryException classify(MessagingErrorCode messagingCode, ErrorCode errorCode,
                                   Integer statusCode, Duration retryAfter, Throwable cause) {
        if (messagingCode == MessagingErrorCode.UNREGISTERED) {
            return PushDeliveryException.invalidToken(cause);
        }
        if (messagingCode == MessagingErrorCode.INVALID_ARGUMENT) {
            return PushDeliveryException.invalidToken(cause);
        }
        if (messagingCode == MessagingErrorCode.QUOTA_EXCEEDED) {
            return PushDeliveryException.retryable(PushFailureCode.RATE_LIMITED,
                    atLeast(retryAfter, Duration.ofMinutes(1)), cause);
        }
        if (messagingCode == MessagingErrorCode.UNAVAILABLE) {
            return PushDeliveryException.retryable(PushFailureCode.PROVIDER_UNAVAILABLE, retryAfter, cause);
        }
        if (messagingCode == MessagingErrorCode.INTERNAL) {
            return PushDeliveryException.retryable(PushFailureCode.PROVIDER_INTERNAL, retryAfter, cause);
        }
        if (messagingCode == MessagingErrorCode.SENDER_ID_MISMATCH) {
            return PushDeliveryException.permanent(PushFailureCode.SENDER_ID_MISMATCH, cause);
        }
        if (messagingCode == MessagingErrorCode.THIRD_PARTY_AUTH_ERROR
                || errorCode == ErrorCode.UNAUTHENTICATED
                || errorCode == ErrorCode.PERMISSION_DENIED) {
            return PushDeliveryException.permanent(PushFailureCode.PROVIDER_AUTHENTICATION, cause);
        }
        if (messagingCode == null && (errorCode == ErrorCode.UNAVAILABLE
                || errorCode == ErrorCode.INTERNAL
                || errorCode == ErrorCode.DEADLINE_EXCEEDED
                || errorCode == ErrorCode.RESOURCE_EXHAUSTED
                || causedByIOException(cause)
                || statusCode != null && List.of(408, 429, 502, 503, 504).contains(statusCode))) {
            PushFailureCode code = statusCode != null && statusCode == 429
                    ? PushFailureCode.RATE_LIMITED : PushFailureCode.PROVIDER_UNAVAILABLE;
            return PushDeliveryException.retryable(code, retryAfter, cause);
        }
        return PushDeliveryException.permanent(PushFailureCode.INVALID_MESSAGE, cause);
    }

    private Duration retryAfter(FirebaseMessagingException exception) {
        if (exception.getHttpResponse() == null) {
            return null;
        }
        Object value = exception.getHttpResponse().getHeaders().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase("Retry-After"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        return parseRetryAfter(value, Instant.now());
    }

    Duration parseRetryAfter(Object value, Instant now) {
        Object candidate = value;
        if (candidate instanceof List<?> values && !values.isEmpty()) {
            candidate = values.getFirst();
        }
        if (candidate == null) {
            return null;
        }
        String text = candidate.toString().trim();
        try {
            long seconds = Long.parseLong(text);
            return seconds < 0 ? null : Duration.ofSeconds(seconds);
        } catch (NumberFormatException ignored) {
            try {
                Instant retryAt = ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                Duration delay = Duration.between(now, retryAt);
                return delay.isNegative() ? null : delay;
            } catch (DateTimeParseException | ArithmeticException invalidDate) {
                return null;
            }
        }
    }

    private boolean causedByIOException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof IOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Duration atLeast(Duration duration, Duration minimum) {
        return duration == null || duration.compareTo(minimum) < 0 ? minimum : duration;
    }

    private static void requirePositiveTimeout(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException();
        }
    }

    private void validateDataKeys(Map<String, String> data) {
        for (String key : data.keySet()) {
            String normalized = key.toLowerCase(Locale.ROOT);
            if (key.isBlank() || normalized.equals("from") || normalized.equals("message_type")
                    || normalized.equals("gcm") || normalized.startsWith("google.")
                    || normalized.startsWith("gcm.")) {
                throw new IllegalArgumentException();
            }
        }
    }
}
