package com.exskylab.koala.core.properties;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "push.delivery")
@Validated
@Getter
@Setter
public class PushDeliveryProperties {

    private static final Duration MAXIMUM_PROVIDER_DEFERRAL = Duration.ofDays(7);

    @Min(1)
    @Max(10)
    private int maxAttempts = 3;
    private Duration initialBackoff = Duration.ofMinutes(1);
    private Duration maxBackoff = Duration.ofMinutes(15);
    private Duration maxProviderDeferral = Duration.ofHours(24);
    private Duration claimTimeout = Duration.ofMinutes(10);
    @Min(1)
    @Max(1000)
    private int batchSize = 100;

    @AssertTrue(message = "push delivery durations must be positive, ordered and operationally bounded")
    public boolean isDurationConfigurationValid() {
        return positive(initialBackoff) && positive(maxBackoff) && positive(maxProviderDeferral)
                && positive(claimTimeout)
                && maxBackoff.compareTo(initialBackoff) >= 0
                && maxProviderDeferral.compareTo(maxBackoff) >= 0
                && maxProviderDeferral.compareTo(MAXIMUM_PROVIDER_DEFERRAL) <= 0;
    }

    private boolean positive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
