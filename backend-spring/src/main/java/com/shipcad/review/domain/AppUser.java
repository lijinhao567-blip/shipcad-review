package com.shipcad.review.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

@Entity
public class AppUser {
    @Id
    public String id;
    public String username;
    public String displayName;
    public String passwordHash;
    @Enumerated(EnumType.STRING)
    public UserRole role;
}
