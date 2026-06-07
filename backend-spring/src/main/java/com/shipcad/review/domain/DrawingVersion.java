package com.shipcad.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import java.time.Instant;

@Entity
public class DrawingVersion {
    @Id
    public String id;
    public String drawingId;
    public String versionNo;
    public String fileName;
    public String filePath;
    public String storageMode;
    @Column(length = 1024)
    public String fileObjectKey;
    public String fileSha256;
    public String uploadedBy;
    public Instant uploadedAt;
    public String parseStatus;
    @Lob
    @Column(length = 200000)
    public String parseSummaryJson;
}
