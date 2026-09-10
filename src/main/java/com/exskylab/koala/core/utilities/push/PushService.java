package com.exskylab.koala.core.utilities.push;

import java.time.Duration;

public interface PushService {

    String send(PushMessage message, Duration timeout);
}
