package com.exskylab.koala.core.logging;

import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class ScheduledLogContextConfig {

    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        ThreadPoolTaskScheduler scheduler = builder.build();
        scheduler.setTaskDecorator(new ScheduledLogContextDecorator());
        return scheduler;
    }
}
