package com.shipcad.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.shipcad.review.domain.AppUser;
import com.shipcad.review.domain.IssueStatus;
import com.shipcad.review.domain.Permission;
import com.shipcad.review.domain.UserRole;
import org.junit.jupiter.api.Test;

class AuthorizationServiceTest {
    private final AuthorizationService access = new AuthorizationService(mock(AuditService.class));

    @Test
    void roleMatrixSeparatesAuthoringReviewAndReadOnlyCapabilities() {
        assertThat(access.has(user(UserRole.ADMIN), Permission.AUDIT_VIEW)).isTrue();
        assertThat(access.has(user(UserRole.REVIEW_EXPERT), Permission.REVIEW_EXECUTE)).isTrue();
        assertThat(access.has(user(UserRole.REVIEW_EXPERT), Permission.PROJECT_WRITE)).isFalse();
        assertThat(access.has(user(UserRole.DESIGN_ENGINEER), Permission.VERSION_UPLOAD)).isTrue();
        assertThat(access.has(user(UserRole.DESIGN_ENGINEER), Permission.REVIEW_EXECUTE)).isFalse();
        assertThat(access.permissions(user(UserRole.VIEWER))).isEmpty();
    }

    @Test
    void designEngineerCanSubmitRemediationButCannotCloseOrReopenIssue() {
        AppUser engineer = user(UserRole.DESIGN_ENGINEER);

        access.requireIssueUpdate(engineer, IssueStatus.READY_FOR_REVIEW);

        assertThatThrownBy(() -> access.requireIssueUpdate(engineer, IssueStatus.CLOSED))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> access.requireIssueUpdate(engineer, IssueStatus.OPEN))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    private AppUser user(UserRole role) {
        AppUser user = new AppUser();
        user.id = "user_" + role.name().toLowerCase();
        user.username = role.name().toLowerCase();
        user.role = role;
        return user;
    }
}
