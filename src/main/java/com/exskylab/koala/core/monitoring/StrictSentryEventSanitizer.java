package com.exskylab.koala.core.monitoring;

import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class StrictSentryEventSanitizer implements SentryOptions.BeforeSendCallback {

    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Z][A-Z0-9_.-]{0,95}");

    @Override
    public SentryEvent execute(SentryEvent event, Hint hint) {
        event.setRequest(null);
        event.setUser(null);
        event.setThrowable(null);
        event.setExtras(new HashMap<>());
        event.setBreadcrumbs(new ArrayList<>());
        event.getContexts().entrySet().clear();
        event.setTags(safeTags(event.getTags()));
        event.setTransaction(null);
        event.setFingerprints(List.of());
        event.setModules(Map.of());
        event.setServerName(null);
        event.setUnknown(null);

        Message message = new Message();
        message.setMessage("Backend error");
        event.setMessage(message);

        if (event.getExceptions() != null) {
            event.getExceptions().forEach(exception -> {
                exception.setValue("[redacted]");
                exception.setModule(null);
                exception.setUnknown(null);
            });
        }
        return event;
    }

    private Map<String, String> safeTags(Map<String, String> tags) {
        if (tags == null) {
            return Map.of();
        }
        Map<String, String> safe = new HashMap<>();
        copySafe(tags, safe, "safe.code");
        copySafe(tags, safe, "safe.type");
        return safe;
    }

    private void copySafe(Map<String, String> source, Map<String, String> target, String key) {
        String value = source.get(key);
        if (value != null && SAFE_VALUE.matcher(value).matches()) {
            target.put(key, value);
        }
    }
}
