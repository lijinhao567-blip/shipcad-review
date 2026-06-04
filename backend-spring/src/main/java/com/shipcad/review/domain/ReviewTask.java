package com.shipcad.review.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
public class ReviewTask {
    @Id
    public String id;
    public String versionId;
    public String status;
    public String stage;
    public Instant startedAt;
    public Instant finishedAt;
    public int issueCount;
    public String errorMessage;
    public Boolean autoVision;
    public Boolean autoOcr;
    public Boolean forceRender;
    public Double visionConfidence;
    public Double ocrConfidence;
    @Transient
    public List<ReviewTaskStep> steps = new ArrayList<>();
}
