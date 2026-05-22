package com.shipcad.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
public class RemediationRecord {
    @Id
    public String id;
    public String issueId;
    public String operator;
    public String action;
    @Column(length = 4000)
    public String note;
    public Instant createdAt;
}
