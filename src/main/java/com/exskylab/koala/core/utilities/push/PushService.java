package com.exskylab.koala.core.utilities.push;

import java.time.Duration;
import java.util.Map;

public interface PushService {

    String sendPush(String pushToken, String title, String body, Map<String, String> dataPayload,
                    Duration timeout);
}
