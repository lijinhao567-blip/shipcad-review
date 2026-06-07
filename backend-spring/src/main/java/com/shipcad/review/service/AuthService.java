package com.shipcad.review.service;

import com.shipcad.review.domain.AppUser;
import com.shipcad.review.domain.AuthSession;
import com.shipcad.review.repo.AppUserRepository;
import com.shipcad.review.repo.AuthSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final AppUserRepository users;
    private final AuthSessionRepository sessions;
    private final AuditService audit;
    private final PasswordPolicy passwordPolicy;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration sessionDuration;

    public AuthService(
            AppUserRepository users,
            AuthSessionRepository sessions,
            AuditService audit,
            PasswordPolicy passwordPolicy,
            @Value("${shipcad.security.session-hours:8}") long sessionHours
    ) {
        this.users = users;
        this.sessions = sessions;
        this.audit = audit;
        this.passwordPolicy = passwordPolicy;
        this.sessionDuration = Duration.ofHours(Math.max(1, Math.min(sessionHours, 168)));
    }

    public String encode(String password) {
        return encoder.encode(password);
    }

    public boolean matches(String rawPassword, String passwordHash) {
        return rawPassword != null && passwordHash != null && encoder.matches(rawPassword, passwordHash);
    }

    public void validatePassword(String password) {
        passwordPolicy.validate(password);
    }

    @Transactional
    public LoginResult login(String username, String password) {
        String normalizedUsername = username == null ? "" : username.trim();
        AppUser user = users.findByUsername(normalizedUsername).orElse(null);
        if (user == null || !matches(password, user.passwordHash)) {
            audit.record(normalizedUsername.isBlank() ? "anonymous" : normalizedUsername,
                    "LOGIN_FAILED", "user", normalizedUsername, Map.of("reason", "BAD_CREDENTIALS"));
            throw new SecurityException("用户名或密码错误");
        }
        if (!isEnabled(user)) {
            audit.record(user.username, "LOGIN_FAILED", "user", user.id, Map.of("reason", "USER_DISABLED"));
            throw new SecurityException("用户已停用");
        }

        Instant now = Ids.now();
        String token = nextToken();
        AuthSession session = new AuthSession();
        session.id = Ids.next("session");
        session.tokenHash = hashToken(token);
        session.userId = user.id;
        session.createdAt = now;
        session.expiresAt = now.plus(sessionDuration);
        session.lastUsedAt = now;
        sessions.save(session);

        user.lastLoginAt = now;
        user.updatedAt = now;
        users.save(user);
        sessions.deleteByExpiresAtBefore(now.minus(Duration.ofDays(7)));
        audit.record(user.username, "LOGIN_SUCCESS", "user", user.id, Map.of("role", user.role.name()));
        return new LoginResult(token, session.expiresAt, user);
    }

    @Transactional(noRollbackFor = SecurityException.class)
    public AppUser requireUser(String authorization) {
        SessionContext context = requireSession(authorization);
        AppUser user = users.findById(context.session().userId).orElseThrow(() -> new SecurityException("用户不存在"));
        if (!isEnabled(user)) {
            revoke(context.session(), Ids.now());
            throw new SecurityException("用户已停用");
        }
        return user;
    }

    @Transactional(noRollbackFor = SecurityException.class)
    public Instant sessionExpiresAt(String authorization) {
        return requireSession(authorization).session().expiresAt;
    }

    @Transactional
    public void logout(String authorization) {
        String token = bearerToken(authorization);
        if (token.isBlank()) {
            return;
        }
        sessions.findByTokenHash(hashToken(token)).ifPresent(session -> {
            Instant now = Ids.now();
            revoke(session, now);
            users.findById(session.userId).ifPresent(user ->
                    audit.record(user.username, "LOGOUT", "session", session.id, Map.of("revokedAt", now)));
        });
    }

    @Transactional
    public int revokeSessionsForUser(String userId, String reason, String actor) {
        Instant now = Ids.now();
        int count = 0;
        for (AuthSession session : sessions.findByUserIdAndRevokedAtIsNull(userId)) {
            if (session.expiresAt != null && session.expiresAt.isAfter(now)) {
                revoke(session, now);
                count++;
            }
        }
        if (count > 0) {
            audit.record(actor, "SESSIONS_REVOKED", "user", userId, Map.of("reason", reason, "count", count));
        }
        return count;
    }

    private SessionContext requireSession(String authorization) {
        String token = bearerToken(authorization);
        if (token.isBlank()) {
            throw new SecurityException("未登录或登录已过期");
        }
        AuthSession session = sessions.findByTokenHash(hashToken(token))
                .orElseThrow(() -> new SecurityException("未登录或登录已过期"));
        Instant now = Ids.now();
        if (session.revokedAt != null || session.expiresAt == null || !session.expiresAt.isAfter(now)) {
            if (session.revokedAt == null) {
                revoke(session, now);
            }
            throw new SecurityException("未登录或登录已过期");
        }
        if (session.lastUsedAt == null || session.lastUsedAt.isBefore(now.minus(Duration.ofMinutes(5)))) {
            session.lastUsedAt = now;
            sessions.save(session);
        }
        return new SessionContext(session);
    }

    private boolean isEnabled(AppUser user) {
        return !Boolean.FALSE.equals(user.enabled);
    }

    private void revoke(AuthSession session, Instant now) {
        if (session.revokedAt == null) {
            session.revokedAt = now;
            sessions.save(session);
        }
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return "";
        }
        return authorization.substring("Bearer ".length()).trim();
    }

    private String nextToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record LoginResult(String token, Instant expiresAt, AppUser user) {
    }

    private record SessionContext(AuthSession session) {
    }
}
