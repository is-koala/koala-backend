package com.exskylab.koala.core.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "push.firebase")
@Getter
@Setter
public class FirebasePushProperties {

    private boolean enabled;
    private String projectId;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(10);
    private Duration writeTimeout = Duration.ofSeconds(10);
}
