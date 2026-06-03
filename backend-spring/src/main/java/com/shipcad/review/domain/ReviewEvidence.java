package com.shipcad.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import java.time.Instant;

@Entity
public class ReviewEvidence {
    @Id
    public String id;
    public String issueId;
    public String taskId;
    public String versionId;
    public String ruleCode;
    @Enumerated(EnumType.STRING)
    public EvidenceType evidenceType;
    public String sourceId;
    public String sourceLabel;
    @Column(length = 4000)
    public String summary;
    @Lob
    @Column(length = 200000)
    public String payloadJson;
    public Double confidence;
    public Instant createdAt;
}
