package com.shipcad.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.shipcad.review.domain.AppUser;
import com.shipcad.review.domain.UserRole;
import com.shipcad.review.dto.ApiDtos.ChangePasswordRequest;
import com.shipcad.review.dto.ApiDtos.CreateUserRequest;
import com.shipcad.review.dto.ApiDtos.UpdateUserRequest;
import com.shipcad.review.repo.AppUserRepository;
import com.shipcad.review.repo.AuditLogRepository;
import com.shipcad.review.repo.AuthSessionRepository;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-session-test;DB_CLOSE_DELAY=-1",
        "spring.profiles.active=test",
        "shipcad.security.seed-dev-users=false"
})
class AuthSessionIntegrationTest {
    @Autowired
    private AppUserRepository users;
    @Autowired
    private AuthSessionRepository sessions;
    @Autowired
    private AuditService audit;
    @Autowired
    private AuditLogRepository auditLogs;
    @Autowired
    private PasswordPolicy passwordPolicy;
    @Autowired
    private AuthService auth;
    @Autowired
    private UserManagementService userManagement;

    private AppUser admin;

    @BeforeEach
    void setUp() {
        sessions.deleteAll();
        users.deleteAll();
        auditLogs.deleteAll();
        admin = new AppUser();
        admin.id = "user_test_admin";
        admin.username = "test_admin";
        admin.displayName = "Test Admin";
        admin.passwordHash = auth.encode("AdminPassword123");
        admin.role = UserRole.ADMIN;
        admin.enabled = true;
        admin.createdAt = Ids.now();
        admin.updatedAt = admin.createdAt;
        admin.passwordChangedAt = admin.createdAt;
        users.save(admin);
    }

    @Test
    void persistedSessionSurvivesAuthServiceRecreationAndLogoutRevokesIt() {
        AuthService.LoginResult login = auth.login("test_admin", "AdminPassword123");
        AuthService recreated = new AuthService(users, sessions, audit, passwordPolicy, 8);

        assertThat(recreated.requireUser("Bearer " + login.token()).id).isEqualTo(admin.id);
        assertThat(recreated.sessionExpiresAt("Bearer " + login.token()))
                .isCloseTo(login.expiresAt(), within(1, ChronoUnit.MILLIS));

        recreated.logout("Bearer " + login.token());

        assertThatThrownBy(() -> auth.requireUser("Bearer " + login.token()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void expiredSessionIsRejectedAndMarkedRevoked() {
        AuthService.LoginResult login = auth.login("test_admin", "AdminPassword123");
        var session = sessions.findAll().get(0);
        session.expiresAt = Ids.now().minus(Duration.ofMinutes(1));
        sessions.save(session);

        assertThatThrownBy(() -> auth.requireUser("Bearer " + login.token()))
                .isInstanceOf(SecurityException.class);
        assertThat(sessions.findById(session.id).orElseThrow().revokedAt).isNotNull();
    }

    @Test
    void securityChangesAndPasswordChangeRevokeExistingSessions() {
        AppUser engineer = userManagement.create(
                new CreateUserRequest("test_engineer", "Test Engineer", UserRole.DESIGN_ENGINEER, "EngineerPassword123", true),
                admin
        );
        AuthService.LoginResult firstLogin = auth.login(engineer.username, "EngineerPassword123");

        userManagement.update(engineer.id, new UpdateUserRequest("Test Engineer", UserRole.VIEWER, true), admin);

        assertThatThrownBy(() -> auth.requireUser("Bearer " + firstLogin.token()))
                .isInstanceOf(SecurityException.class);

        AuthService.LoginResult secondLogin = auth.login(engineer.username, "EngineerPassword123");
        userManagement.changeOwnPassword(
                new ChangePasswordRequest("EngineerPassword123", "EngineerPassword456"),
                engineer
        );

        assertThatThrownBy(() -> auth.requireUser("Bearer " + secondLogin.token()))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> auth.login(engineer.username, "EngineerPassword123"))
                .isInstanceOf(SecurityException.class);
        assertThat(auth.login(engineer.username, "EngineerPassword456").user().id).isEqualTo(engineer.id);
    }

    @Test
    void failedLoginRemainsAuditedAfterAuthenticationException() {
        assertThatThrownBy(() -> auth.login("test_admin", "wrong-password"))
                .isInstanceOf(SecurityException.class);

        assertThat(auditLogs.findAll())
                .anySatisfy(log -> {
                    assertThat(log.actor).isEqualTo("test_admin");
                    assertThat(log.action).isEqualTo("LOGIN_FAILED");
                });
    }
}
