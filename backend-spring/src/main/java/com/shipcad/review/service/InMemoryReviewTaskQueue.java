package com.shipcad.review.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "shipcad.review-queue.mode", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryReviewTaskQueue implements ReviewTaskQueue {
    private final ThreadPoolTaskExecutor executor;
    private final ReviewTaskRunner runner;
    private final TransactionTemplate transactionTemplate;

    public InMemoryReviewTaskQueue(
            ThreadPoolTaskExecutor reviewTaskExecutor,
            @Lazy ReviewTaskRunner runner,
            PlatformTransactionManager transactionManager
    ) {
        this.executor = reviewTaskExecutor;
        this.runner = runner;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void enqueue(String taskId, String actorUsername) {
        executor.execute(() -> transactionTemplate.executeWithoutResult(
                status -> runner.runQueuedReviewTask(taskId, actorUsername)
        ));
    }

    @Override
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "in-memory");
        result.put("status", "ok");
        ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
        result.put("activeCount", pool.getActiveCount());
        result.put("poolSize", pool.getPoolSize());
        result.put("queuedCount", pool.getQueue().size());
        result.put("remainingCapacity", pool.getQueue().remainingCapacity());
        return result;
    }
}
