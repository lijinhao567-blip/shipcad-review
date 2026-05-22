package com.shipcad.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

@Entity
public class ParsedEntity {
    @Id
    public String id;
    public String versionId;
    public String entityType;
    public String layerName;
    @Column(length = 2000)
    public String textValue;
    public String blockName;
    public Double x;
    public Double y;
    @Lob
    @Column(length = 200000)
    public String rawJson;
}
