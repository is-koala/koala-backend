package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.properties.NotificationDispatchProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

@Component
public class NotificationDispatchBackoff {

    private final NotificationDispatchProperties properties;
    private final DoubleSupplier random;

    @Autowired
    public NotificationDispatchBackoff(NotificationDispatchProperties properties) {
        this(properties, () -> ThreadLocalRandom.current().nextDouble());
    }

    NotificationDispatchBackoff(NotificationDispatchProperties properties, DoubleSupplier random) {
        this.properties = properties;
        this.random = random;
    }

    public Duration delayFor(int attempts) {
        long multiplier = 1L << Math.min(Math.max(attempts - 1, 0), 20);
        Duration bounded = properties.getInitialBackoff().multipliedBy(multiplier);
        if (bounded.compareTo(properties.getMaxBackoff()) > 0) {
            bounded = properties.getMaxBackoff();
        }
        double jitter = Math.clamp(properties.getJitterFactor(), 0.0, 1.0);
        double factor = (1.0 - jitter) + random.getAsDouble() * jitter;
        return Duration.ofMillis(Math.max(1L, (long) (bounded.toMillis() * factor)));
    }
}
