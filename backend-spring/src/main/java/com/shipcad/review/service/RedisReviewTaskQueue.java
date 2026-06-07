package com.shipcad.review.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "shipcad.review-queue.mode", havingValue = "redis")
public class RedisReviewTaskQueue implements ReviewTaskQueue, SmartLifecycle {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final ThreadPoolTaskExecutor executor;
    private final ReviewTaskRunner runner;
    private final TransactionTemplate transactionTemplate;
    private final String queueKey;
    private final String processingKey;
    private final int capacity;
    private final Duration pollTimeout;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean processingRecovered = new AtomicBoolean(false);
    private Thread workerThread;

    public RedisReviewTaskQueue(
            StringRedisTemplate redis,
            ObjectMapper mapper,
            ThreadPoolTaskExecutor reviewTaskExecutor,
            @Lazy ReviewTaskRunner runner,
            PlatformTransactionManager transactionManager,
            @Value("${shipcad.review-queue.redis.queue-key}") String queueKey,
            @Value("${shipcad.review-queue.redis.processing-key}") String processingKey,
            @Value("${shipcad.review-queue.capacity}") int capacity,
            @Value("${shipcad.review-queue.redis.poll-seconds}") int pollSeconds
    ) {
        this.redis = redis;
        this.mapper = mapper;
        this.executor = reviewTaskExecutor;
        this.runner = runner;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.queueKey = queueKey;
        this.processingKey = processingKey;
        this.capacity = capacity;
        this.pollTimeout = Duration.ofSeconds(Math.max(1, pollSeconds));
    }

    @Override
    public void enqueue(String taskId, String actorUsername) {
        Long queued = redis.opsForList().size(queueKey);
        if (queued != null && queued >= capacity) {
            throw new IllegalStateException("审查任务队列已满，请稍后重试");
        }
        redis.opsForList().leftPush(queueKey, toJson(new RedisReviewJob(taskId, actorUsername, Instant.now().toString())));
    }

    @Override
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "redis");
        result.put("queueKey", queueKey);
        result.put("processingKey", processingKey);
        result.put("workerRunning", running.get());
        ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
        result.put("activeCount", pool.getActiveCount());
        result.put("localQueuedCount", pool.getQueue().size());
        try {
            String ping = null;
            if (redis.getConnectionFactory() != null) {
                RedisConnection connection = redis.getConnectionFactory().getConnection();
                try {
                    ping = connection.ping();
                } finally {
                    connection.close();
                }
            }
            result.put("status", "PONG".equalsIgnoreCase(ping) ? "ok" : "down");
            result.put("redisPing", ping);
            result.put("queuedCount", safeSize(queueKey));
            result.put("processingCount", safeSize(processingKey));
        } catch (RuntimeException exception) {
            result.put("status", "down");
            result.put("error", exception.getMessage());
        }
        return result;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        workerThread = new Thread(this::pollLoop, "redis-review-task-poller");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    @Override
    public void stop() {
        running.set(false);
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private void pollLoop() {
        while (running.get()) {
            try {
                if (!processingRecovered.get()) {
                    requeueInterruptedJobs();
                    processingRecovered.set(true);
                }
                String payload = redis.opsForList().rightPopAndLeftPush(queueKey, processingKey, pollTimeout);
                if (payload != null && !payload.isBlank()) {
                    dispatch(payload);
                }
            } catch (RedisConnectionFailureException exception) {
                sleepQuietly(1000);
            } catch (RuntimeException exception) {
                sleepQuietly(500);
            }
        }
    }

    private void dispatch(String payload) {
        try {
            executor.execute(() -> runPayload(payload));
        } catch (RejectedExecutionException exception) {
            redis.opsForList().remove(processingKey, 1, payload);
            redis.opsForList().leftPush(queueKey, payload);
            sleepQuietly(250);
        }
    }

    private void runPayload(String payload) {
        try {
            RedisReviewJob job = mapper.readValue(payload, RedisReviewJob.class);
            transactionTemplate.executeWithoutResult(
                    status -> runner.runQueuedReviewTask(job.taskId(), job.actorUsername())
            );
        } catch (Exception ignored) {
            // ReviewPlatformService stores task failures in the database. Malformed queue
            // messages are acknowledged here so they cannot poison the queue forever.
        } finally {
            redis.opsForList().remove(processingKey, 1, payload);
        }
    }

    private void requeueInterruptedJobs() {
        String payload;
        while ((payload = redis.opsForList().rightPop(processingKey)) != null) {
            redis.opsForList().leftPush(queueKey, payload);
        }
    }

    private Long safeSize(String key) {
        Long size = redis.opsForList().size(key);
        return size == null ? 0L : size;
    }

    private String toJson(RedisReviewJob job) {
        try {
            return mapper.writeValueAsString(job);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("审查任务入队序列化失败", exception);
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private record RedisReviewJob(String taskId, String actorUsername, String enqueuedAt) {
    }
}
