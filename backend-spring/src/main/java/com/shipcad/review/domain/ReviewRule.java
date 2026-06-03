package com.shipcad.review.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

@Entity
public class ReviewRule {
    @Id
    public String id;
    public String code;
    public String name;
    public String description;
    @Enumerated(EnumType.STRING)
    public Severity severity;
    public boolean enabled;
    public String knowledgeClauseCode;
}
