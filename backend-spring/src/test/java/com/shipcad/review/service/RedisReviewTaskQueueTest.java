package com.shipcad.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class RedisReviewTaskQueueTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private ThreadPoolTaskExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.initialize();
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void enqueueStoresReviewTaskPayloadInRedisList() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ListOperations<String, String> lists = mockListOperations();
        when(redis.opsForList()).thenReturn(lists);
        when(lists.size("queue")).thenReturn(0L);
        RedisReviewTaskQueue queue = queue(redis, 10);

        queue.enqueue("task_1", "admin");

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(lists).leftPush(eq("queue"), payload.capture());
        Map<String, Object> parsed = mapper.readValue(payload.getValue(), new TypeReference<>() {
        });
        assertThat(parsed)
                .containsEntry("taskId", "task_1")
                .containsEntry("actorUsername", "admin")
                .containsKey("enqueuedAt");
    }

    @Test
    void enqueueRejectsWhenRedisQueueIsFull() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ListOperations<String, String> lists = mockListOperations();
        when(redis.opsForList()).thenReturn(lists);
        when(lists.size("queue")).thenReturn(2L);
        RedisReviewTaskQueue queue = queue(redis, 2);

        assertThatThrownBy(() -> queue.enqueue("task_1", "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("队列已满");
    }

    @Test
    void startRequeuesJobsLeftInProcessingList() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ListOperations<String, String> lists = mockListOperations();
        String stalePayload = "{\"taskId\":\"task_stale\",\"actorUsername\":\"admin\",\"enqueuedAt\":\"2026-06-08T00:00:00Z\"}";
        when(redis.opsForList()).thenReturn(lists);
        when(lists.rightPop("processing")).thenReturn(stalePayload, null);
        when(lists.rightPopAndLeftPush(eq("queue"), eq("processing"), any(Duration.class))).thenReturn(null);
        RedisReviewTaskQueue queue = queue(redis, 10);

        try {
            queue.start();

            verify(lists, timeout(1000)).leftPush("queue", stalePayload);
            verify(lists, timeout(1000).times(2)).rightPop("processing");
        } finally {
            queue.stop();
        }
    }

    @Test
    void rejectedLocalDispatchRequeuesPayload() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ListOperations<String, String> lists = mockListOperations();
        ThreadPoolTaskExecutor rejectingExecutor = mock(ThreadPoolTaskExecutor.class);
        String payload = "{\"taskId\":\"task_1\",\"actorUsername\":\"admin\",\"enqueuedAt\":\"2026-06-08T00:00:00Z\"}";
        when(redis.opsForList()).thenReturn(lists);
        when(lists.rightPop("processing")).thenReturn(null);
        when(lists.rightPopAndLeftPush(eq("queue"), eq("processing"), any(Duration.class))).thenReturn(payload, null);
        doThrow(new RejectedExecutionException("full")).when(rejectingExecutor).execute(any(Runnable.class));
        RedisReviewTaskQueue queue = queue(redis, 10, rejectingExecutor);

        try {
            queue.start();

            verify(lists, timeout(1000)).remove("processing", 1, payload);
            verify(lists, timeout(1000)).leftPush("queue", payload);
        } finally {
            queue.stop();
        }
    }

    @SuppressWarnings("unchecked")
    private ListOperations<String, String> mockListOperations() {
        return mock(ListOperations.class);
    }

    private RedisReviewTaskQueue queue(StringRedisTemplate redis, int capacity) {
        return queue(redis, capacity, executor);
    }

    private RedisReviewTaskQueue queue(StringRedisTemplate redis, int capacity, ThreadPoolTaskExecutor taskExecutor) {
        return new RedisReviewTaskQueue(
                redis,
                mapper,
                taskExecutor,
                (taskId, actorUsername) -> {
                },
                new NoopTransactionManager(),
                "queue",
                "processing",
                capacity,
                1
        );
    }

    private static class NoopTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
