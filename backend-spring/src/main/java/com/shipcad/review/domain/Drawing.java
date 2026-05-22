package com.shipcad.review.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
public class Drawing {
    @Id
    public String id;
    public String projectId;
    public String drawingNo;
    public String title;
    public String discipline;
    public Instant createdAt;
}
