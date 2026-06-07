package com.shipcad.review.service;

import com.shipcad.review.domain.AppUser;
import com.shipcad.review.domain.IssueStatus;
import com.shipcad.review.domain.Permission;
import com.shipcad.review.domain.UserRole;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {
    private static final Set<Permission> ALL_PERMISSIONS = EnumSet.allOf(Permission.class);
    private static final Map<UserRole, Set<Permission>> ROLE_PERMISSIONS = Map.of(
            UserRole.ADMIN, ALL_PERMISSIONS,
            UserRole.REVIEW_EXPERT, EnumSet.of(
                    Permission.EVIDENCE_COLLECT,
                    Permission.REVIEW_EXECUTE,
                    Permission.ISSUE_REMEDIATE,
                    Permission.ISSUE_REVIEW_DECIDE,
                    Permission.REPORT_GENERATE
            ),
            UserRole.DESIGN_ENGINEER, EnumSet.of(
                    Permission.PROJECT_WRITE,
                    Permission.VERSION_UPLOAD,
                    Permission.EVIDENCE_COLLECT,
                    Permission.ISSUE_REMEDIATE
            ),
            UserRole.VIEWER, EnumSet.noneOf(Permission.class)
    );

    private final AuditService audit;

    public AuthorizationService(AuditService audit) {
        this.audit = audit;
    }

    public List<String> permissions(AppUser user) {
        return permissionsOf(user).stream().map(Enum::name).sorted().toList();
    }

    public boolean has(AppUser user, Permission permission) {
        return permissionsOf(user).contains(permission);
    }

    public void require(AppUser user, Permission permission) {
        if (has(user, permission)) {
            return;
        }
        String username = user == null || user.username == null ? "unknown" : user.username;
        String role = user == null || user.role == null ? "UNKNOWN" : user.role.name();
        audit.record(username, "ACCESS_DENIED", "permission", permission.name(), Map.of("role", role));
        throw new ForbiddenOperationException("当前角色无权执行此操作：" + permission.name());
    }

    public void requireIssueUpdate(AppUser user, IssueStatus targetStatus) {
        require(user, Permission.ISSUE_REMEDIATE);
        if (targetStatus == IssueStatus.CLOSED || targetStatus == IssueStatus.OPEN) {
            require(user, Permission.ISSUE_REVIEW_DECIDE);
        }
    }

    private Set<Permission> permissionsOf(AppUser user) {
        if (user == null || user.role == null) {
            return Set.of();
        }
        return ROLE_PERMISSIONS.getOrDefault(user.role, Set.of());
    }
}
