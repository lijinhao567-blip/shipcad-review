package com.shipcad.review.service;

import java.util.Map;

public interface ReviewTaskQueue {
    void enqueue(String taskId, String actorUsername);

    Map<String, Object> health();
}
