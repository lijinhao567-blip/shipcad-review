package com.shipcad.review.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
public class ReviewTask {
    @Id
    public String id;
    public String versionId;
    public String status;
    public Instant startedAt;
    public Instant finishedAt;
    public int issueCount;
    public String errorMessage;
}
