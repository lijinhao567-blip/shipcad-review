package com.shipcad.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
public class ReviewIssue {
    @Id
    public String id;
    public String taskId;
    public String versionId;
    public String ruleCode;
    public String title;
    @Column(length = 4000)
    public String description;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(Types.VARCHAR)
    public Severity severity;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(Types.VARCHAR)
    public IssueStatus status;
    public String layerName;
    public String entityRef;
    @Column(length = 4000)
    public String suggestion;
    public Instant createdAt;
    public Instant updatedAt;
    public String assignee;
    @Transient
    public List<ReviewEvidence> evidences = new ArrayList<>();
    @Transient
    public AiExplanation aiExplanation;
}
