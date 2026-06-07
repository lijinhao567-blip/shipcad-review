package com.shipcad.review.service;

public interface ReviewTaskRunner {
    void runQueuedReviewTask(String taskId, String actorUsername);
}
