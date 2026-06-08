package com.shipcad.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import java.time.Instant;

@Entity
public class ReportDocument {
    @Id
    public String id;
    public String taskId;
    public String versionId;
    @Lob
    @Column(length = 200000)
    public String content;
    public String storageMode;
    public String contentObjectKey;
    public String contentPath;
    public Long contentSizeBytes;
    public Instant createdAt;
}
