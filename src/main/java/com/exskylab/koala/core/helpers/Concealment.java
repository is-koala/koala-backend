package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class Concealment {

    private Concealment() {
    }

    public static ResourceNotFoundException notFound(String resourceNotFoundKey, Object actorId, Object resourceId,
                                                     String refusal) {
        log.warn("Concealed as not found: actor={} resource={} refusal={}", actorId, resourceId, refusal);
        return new ResourceNotFoundException(resourceNotFoundKey);
    }
}
