package com.shipcad.review.repo;

import com.shipcad.review.domain.AuthSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {
    Optional<AuthSession> findByTokenHash(String tokenHash);

    List<AuthSession> findByUserIdAndRevokedAtIsNull(String userId);

    long deleteByExpiresAtBefore(Instant cutoff);
}
