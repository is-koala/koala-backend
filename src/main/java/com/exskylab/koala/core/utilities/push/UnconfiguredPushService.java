package com.exskylab.koala.core.utilities.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnProperty(prefix = "push.firebase", name = "enabled", havingValue = "false", matchIfMissing = true)
public class UnconfiguredPushService implements PushService {

    @Override
    public String send(PushMessage message) {
        log.warn("Push provider is disabled; delivery will be recorded as a terminal failure");
        throw PushDeliveryException.permanent(PushFailureCode.PROVIDER_NOT_CONFIGURED);
    }
}
