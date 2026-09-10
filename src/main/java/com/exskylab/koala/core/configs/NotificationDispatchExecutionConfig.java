package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.logging.ScheduledLogContextDecorator;
import com.exskylab.koala.core.properties.NotificationDispatchExecutorProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class NotificationDispatchExecutionConfig {

    @Bean(name = "notificationDispatchTriggerScheduler")
    public ThreadPoolTaskScheduler notificationDispatchTriggerScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("notification-dispatch-trigger-");
        scheduler.setTaskDecorator(new ScheduledLogContextDecorator());
        return scheduler;
    }

    @Bean(name = "notificationDispatchExecutor")
    public ThreadPoolTaskExecutor notificationDispatchExecutor(NotificationDispatchExecutorProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getThreads());
        executor.setMaxPoolSize(properties.getThreads());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix("notification-dispatch-worker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        return executor;
    }
}
