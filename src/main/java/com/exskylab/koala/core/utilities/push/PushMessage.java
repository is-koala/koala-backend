package com.exskylab.koala.core.utilities.push;

import java.util.Map;

public record PushMessage(
        String token,
        String title,
        String body,
        Map<String, String> data
) {
}
