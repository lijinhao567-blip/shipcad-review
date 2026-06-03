package com.shipcad.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import java.time.Instant;

@Entity
public class KnowledgeClause {
    @Id
    public String id;
    @Column(unique = true)
    public String code;
    public String title;
    @Lob
    @Column(length = 200000)
    public String content;
    public String source;
    @Column(length = 2000)
    public String tags;
    @Column(length = 4000)
    public String remediationHint;
    public Instant createdAt;
}
