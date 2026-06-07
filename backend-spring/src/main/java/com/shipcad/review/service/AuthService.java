package com.shipcad.review.service;

import com.shipcad.review.domain.AppUser;
import com.shipcad.review.repo.AppUserRepository;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final AppUserRepository users;
    private final AuditService audit;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    public AuthService(AppUserRepository users, AuditService audit) {
        this.users = users;
        this.audit = audit;
    }

    public String encode(String password) {
        return encoder.encode(password);
    }

    public LoginResult login(String username, String password) {
        AppUser user = users.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
        if (!encoder.matches(password, user.passwordHash)) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        String token = Ids.next("token");
        sessions.put(token, user.id);
        audit.record(user.username, "LOGIN_SUCCESS", "user", user.id, Map.of("role", user.role.name()));
        return new LoginResult(token, user);
    }

    public AppUser requireUser(String authorization) {
        String token = authorization == null ? "" : authorization.replace("Bearer ", "").trim();
        String userId = sessions.get(token);
        if (userId == null) {
            throw new SecurityException("未登录或登录已过期");
        }
        return users.findById(userId).orElseThrow(() -> new SecurityException("用户不存在"));
    }

    public record LoginResult(String token, AppUser user) {
    }
}
