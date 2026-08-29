package com.exskylab.koala.core.utilities.push;

import com.exskylab.koala.core.constants.PushMessages;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class UnconfiguredPushService implements PushService {

    @Override
    public String sendPush(String pushToken, String title, String body, Map<String, String> dataPayload) {
        log.warn("Push provider is not configured, the notification is recorded as failed instead of "
                + "sitting in the queue forever.");
        throw new ExternalServiceException(PushMessages.PUSH_PROVIDER_NOT_CONFIGURED);
    }
}
