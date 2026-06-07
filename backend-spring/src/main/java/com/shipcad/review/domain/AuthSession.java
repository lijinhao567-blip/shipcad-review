package com.shipcad.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
        name = "auth_session",
        indexes = {
                @Index(name = "idx_auth_session_token_hash", columnList = "tokenHash", unique = true),
                @Index(name = "idx_auth_session_user_id", columnList = "userId"),
                @Index(name = "idx_auth_session_expires_at", columnList = "expiresAt")
        }
)
public class AuthSession {
    @Id
    public String id;
    @Column(nullable = false, length = 64)
    public String tokenHash;
    @Column(nullable = false)
    public String userId;
    @Column(nullable = false)
    public Instant createdAt;
    @Column(nullable = false)
    public Instant expiresAt;
    public Instant lastUsedAt;
    public Instant revokedAt;
}
