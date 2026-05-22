package com.shipcad.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import java.time.Instant;

@Entity
public class AuditLog {
    @Id
    public String id;
    public String actor;
    public String action;
    public String targetType;
    public String targetId;
    @Lob
    @Column(length = 200000)
    public String detailJson;
    public Instant createdAt;
}
