package com.exskylab.koala.core.utilities.iys;

import com.exskylab.koala.core.constants.IysMessages;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import com.exskylab.koala.entities.IysFailureCode;

public final class IysFailureClassifier {

    public static IysFailureCode classify(Throwable exception) {
        if (!(exception instanceof ExternalServiceException external)) {
            return IysFailureCode.UNKNOWN;
        }
        return switch (external.getMessage()) {
            case IysMessages.PROVIDER_UNAVAILABLE -> IysFailureCode.PROVIDER_UNAVAILABLE;
            case IysMessages.PROVIDER_REJECTED -> IysFailureCode.PROVIDER_REJECTED;
            case IysMessages.INVALID_RESPONSE -> IysFailureCode.INVALID_RESPONSE;
            case IysMessages.REGISTRY_PENDING -> IysFailureCode.REGISTRY_PENDING;
            case IysMessages.RECIPIENT_CIPHER_UNAVAILABLE -> IysFailureCode.SECURE_RECIPIENT_UNAVAILABLE;
            default -> IysFailureCode.UNKNOWN;
        };
    }

    private IysFailureClassifier() {
    }
}
