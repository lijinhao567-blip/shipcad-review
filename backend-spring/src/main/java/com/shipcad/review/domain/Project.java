package com.shipcad.review.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
public class Project {
    @Id
    public String id;
    public String name;
    public String shipNo;
    public String owner;
    public String description;
    public Instant createdAt;
}
