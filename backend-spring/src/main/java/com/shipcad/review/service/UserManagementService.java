package com.shipcad.review.service;

import com.shipcad.review.domain.AppUser;
import com.shipcad.review.domain.UserRole;
import com.shipcad.review.dto.ApiDtos.ChangePasswordRequest;
import com.shipcad.review.dto.ApiDtos.CreateUserRequest;
import com.shipcad.review.dto.ApiDtos.ResetPasswordRequest;
import com.shipcad.review.dto.ApiDtos.UpdateUserRequest;
import com.shipcad.review.repo.AppUserRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserManagementService {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{3,50}$");

    private final AppUserRepository users;
    private final AuthService auth;
    private final AuditService audit;

    public UserManagementService(AppUserRepository users, AuthService auth, AuditService audit) {
        this.users = users;
        this.auth = auth;
        this.audit = audit;
    }

    public List<AppUser> list() {
        return users.findAll().stream()
                .sorted(Comparator.comparing(user -> user.username.toLowerCase(Locale.ROOT)))
                .toList();
    }

    @Transactional
    public AppUser create(CreateUserRequest request, AppUser actor) {
        String username = normalizeUsername(request.username());
        if (users.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (request.role() == null) {
            throw new IllegalArgumentException("角色不能为空");
        }
        auth.validatePassword(request.password());

        AppUser user = new AppUser();
        user.id = Ids.next("user");
        user.username = username;
        user.displayName = requiredDisplayName(request.displayName());
        user.role = request.role();
        user.enabled = request.enabled() == null || request.enabled();
        user.passwordHash = auth.encode(request.password());
        user.createdAt = Ids.now();
        user.updatedAt = user.createdAt;
        user.passwordChangedAt = user.createdAt;
        users.save(user);
        audit.record(actor.username, "USER_CREATE", "user", user.id,
                Map.of("username", user.username, "role", user.role.name(), "enabled", user.enabled));
        return user;
    }

    @Transactional
    public AppUser update(String userId, UpdateUserRequest request, AppUser actor) {
        AppUser user = requireUser(userId);
        UserRole nextRole = request.role() == null ? user.role : request.role();
        boolean nextEnabled = request.enabled() == null ? !Boolean.FALSE.equals(user.enabled) : request.enabled();
        if (user.id.equals(actor.id) && (nextRole != UserRole.ADMIN || !nextEnabled)) {
            throw new IllegalArgumentException("不能撤销当前管理员自身权限或停用自身账号");
        }

        UserRole oldRole = user.role;
        boolean oldEnabled = !Boolean.FALSE.equals(user.enabled);
        if (request.displayName() != null) {
            user.displayName = requiredDisplayName(request.displayName());
        }
        user.role = nextRole;
        user.enabled = nextEnabled;
        user.updatedAt = Ids.now();
        users.save(user);

        boolean securityChanged = oldRole != nextRole || oldEnabled != nextEnabled;
        if (securityChanged) {
            auth.revokeSessionsForUser(user.id, "USER_SECURITY_CHANGED", actor.username);
        }
        audit.record(actor.username, "USER_UPDATE", "user", user.id, Map.of(
                "username", user.username,
                "oldRole", oldRole.name(),
                "role", nextRole.name(),
                "oldEnabled", oldEnabled,
                "enabled", nextEnabled
        ));
        return user;
    }

    @Transactional
    public void resetPassword(String userId, ResetPasswordRequest request, AppUser actor) {
        AppUser user = requireUser(userId);
        auth.validatePassword(request.newPassword());
        user.passwordHash = auth.encode(request.newPassword());
        user.passwordChangedAt = Ids.now();
        user.updatedAt = user.passwordChangedAt;
        users.save(user);
        auth.revokeSessionsForUser(user.id, "PASSWORD_RESET", actor.username);
        audit.record(actor.username, "PASSWORD_RESET", "user", user.id, Map.of("username", user.username));
    }

    @Transactional
    public void changeOwnPassword(ChangePasswordRequest request, AppUser actor) {
        AppUser user = requireUser(actor.id);
        if (!auth.matches(request.currentPassword(), user.passwordHash)) {
            audit.record(actor.username, "PASSWORD_CHANGE_FAILED", "user", user.id,
                    Map.of("reason", "CURRENT_PASSWORD_MISMATCH"));
            throw new IllegalArgumentException("当前密码错误");
        }
        auth.validatePassword(request.newPassword());
        if (auth.matches(request.newPassword(), user.passwordHash)) {
            throw new IllegalArgumentException("新密码不能与当前密码相同");
        }
        user.passwordHash = auth.encode(request.newPassword());
        user.passwordChangedAt = Ids.now();
        user.updatedAt = user.passwordChangedAt;
        users.save(user);
        auth.revokeSessionsForUser(user.id, "PASSWORD_CHANGED", actor.username);
        audit.record(actor.username, "PASSWORD_CHANGED", "user", user.id, Map.of());
    }

    private AppUser requireUser(String userId) {
        return users.findById(userId).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    private String normalizeUsername(String username) {
        String normalized = username == null ? "" : username.trim();
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("用户名需为3-50位字母、数字、点、下划线或连字符");
        }
        return normalized;
    }

    private String requiredDisplayName(String displayName) {
        String normalized = displayName == null ? "" : displayName.trim();
        if (normalized.isBlank() || normalized.length() > 80) {
            throw new IllegalArgumentException("显示名称不能为空且不能超过80个字符");
        }
        return normalized;
    }
}
