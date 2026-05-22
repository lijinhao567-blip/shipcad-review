package com.shipcad.review.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class TaskQueueConfig {
    @Bean
    public ThreadPoolTaskExecutor reviewTaskExecutor(
            @Value("${shipcad.review-queue.core-size}") int coreSize,
            @Value("${shipcad.review-queue.max-size}") int maxSize,
            @Value("${shipcad.review-queue.capacity}") int capacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("review-task-");
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(capacity);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
