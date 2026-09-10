package com.exskylab.koala.core.utilities.push;

import java.time.Duration;

public class PushDeliveryException extends RuntimeException {

    private final PushFailureCode code;
    private final boolean retryable;
    private final boolean invalidToken;
    private final Duration retryAfter;

    private PushDeliveryException(PushFailureCode code, boolean retryable, boolean invalidToken,
                                  Duration retryAfter, Throwable cause) {
        super(code.name(), cause);
        this.code = code;
        this.retryable = retryable;
        this.invalidToken = invalidToken;
        this.retryAfter = retryAfter;
    }

    public static PushDeliveryException permanent(PushFailureCode code) {
        return new PushDeliveryException(code, false, false, null, null);
    }

    public static PushDeliveryException permanent(PushFailureCode code, Throwable cause) {
        return new PushDeliveryException(code, false, false, null, cause);
    }

    public static PushDeliveryException invalidToken(Throwable cause) {
        return new PushDeliveryException(PushFailureCode.INVALID_TOKEN, false, true, null, cause);
    }

    public static PushDeliveryException retryable(PushFailureCode code, Duration retryAfter, Throwable cause) {
        return new PushDeliveryException(code, true, false, retryAfter, cause);
    }

    public PushFailureCode code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }

    public boolean invalidToken() {
        return invalidToken;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
