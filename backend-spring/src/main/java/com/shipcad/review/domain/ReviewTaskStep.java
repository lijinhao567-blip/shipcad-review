package com.shipcad.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
public class ReviewTaskStep {
    @Id
    public String id;
    public String taskId;
    public int stepOrder;
    public String stepCode;
    public String stepName;
    public String status;
    public Instant startedAt;
    public Instant finishedAt;
    @Column(length = 4000)
    public String message;
    @Column(length = 4000)
    public String detailJson;
}
