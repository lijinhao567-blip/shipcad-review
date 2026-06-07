package com.shipcad.review.api;

import com.shipcad.review.domain.AppUser;
import com.shipcad.review.domain.Permission;
import com.shipcad.review.dto.ApiDtos.CreateUserRequest;
import com.shipcad.review.dto.ApiDtos.ManagedUserView;
import com.shipcad.review.dto.ApiDtos.ResetPasswordRequest;
import com.shipcad.review.dto.ApiDtos.UpdateUserRequest;
import com.shipcad.review.service.AuthorizationService;
import com.shipcad.review.service.AuthService;
import com.shipcad.review.service.UserManagementService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController extends BaseController {
    private final AuthorizationService access;
    private final UserManagementService userManagement;

    public UserController(AuthService auth, AuthorizationService access, UserManagementService userManagement) {
        super(auth);
        this.access = access;
        this.userManagement = userManagement;
    }

    @GetMapping
    public List<ManagedUserView> users(@RequestHeader("Authorization") String authorization) {
        var actor = user(authorization);
        access.require(actor, Permission.USER_MANAGE);
        return userManagement.list().stream().map(this::managedView).toList();
    }

    @PostMapping
    public ManagedUserView create(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody CreateUserRequest request
    ) {
        var actor = user(authorization);
        access.require(actor, Permission.USER_MANAGE);
        return managedView(userManagement.create(request, actor));
    }

    @PatchMapping("/{userId}")
    public ManagedUserView update(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String userId,
            @RequestBody UpdateUserRequest request
    ) {
        var actor = user(authorization);
        access.require(actor, Permission.USER_MANAGE);
        return managedView(userManagement.update(userId, request, actor));
    }

    @PostMapping("/{userId}/reset-password")
    public Map<String, String> resetPassword(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String userId,
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        var actor = user(authorization);
        access.require(actor, Permission.USER_MANAGE);
        userManagement.resetPassword(userId, request, actor);
        return Map.of("status", "password_reset");
    }

    private ManagedUserView managedView(AppUser user) {
        return new ManagedUserView(
                user.id,
                user.username,
                user.displayName,
                user.role.name(),
                !Boolean.FALSE.equals(user.enabled),
                user.createdAt,
                user.updatedAt,
                user.passwordChangedAt,
                user.lastLoginAt
        );
    }
}
