package com.exskylab.koala;

import com.exskylab.koala.core.properties.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@EnableScheduling
public class KoalaApplication {

    public static void main(String[] args) {
        SpringApplication.run(KoalaApplication.class, args);
    }

}
