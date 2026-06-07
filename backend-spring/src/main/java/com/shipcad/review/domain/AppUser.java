package com.shipcad.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
public class AppUser {
    @Id
    public String id;
    @Column(nullable = false, unique = true, length = 50)
    public String username;
    @Column(nullable = false, length = 80)
    public String displayName;
    @Column(nullable = false, length = 100)
    public String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    public UserRole role;
    public Boolean enabled;
    public Instant createdAt;
    public Instant updatedAt;
    public Instant passwordChangedAt;
    public Instant lastLoginAt;
}
