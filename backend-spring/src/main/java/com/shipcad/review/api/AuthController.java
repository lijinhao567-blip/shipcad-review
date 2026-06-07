package com.shipcad.review.api;

import com.shipcad.review.dto.ApiDtos.AccessView;
import com.shipcad.review.dto.ApiDtos.ChangePasswordRequest;
import com.shipcad.review.dto.ApiDtos.LoginRequest;
import com.shipcad.review.dto.ApiDtos.LoginResponse;
import com.shipcad.review.service.AuthorizationService;
import com.shipcad.review.service.AuthService;
import com.shipcad.review.service.UserManagementService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController extends BaseController {
    private final AuthorizationService access;
    private final UserManagementService userManagement;

    public AuthController(AuthService auth, AuthorizationService access, UserManagementService userManagement) {
        super(auth);
        this.access = access;
        this.userManagement = userManagement;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = auth.login(request.username(), request.password());
        return new LoginResponse(result.token(), result.expiresAt(), view(result.user()), access.permissions(result.user()));
    }

    @GetMapping("/me")
    public AccessView me(@RequestHeader("Authorization") String authorization) {
        var current = user(authorization);
        return new AccessView(view(current), access.permissions(current), auth.sessionExpiresAt(authorization));
    }

    @PostMapping("/logout")
    public Map<String, String> logout(@RequestHeader("Authorization") String authorization) {
        auth.logout(authorization);
        return Map.of("status", "logged_out");
    }

    @PostMapping("/change-password")
    public Map<String, String> changePassword(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        userManagement.changeOwnPassword(request, user(authorization));
        return Map.of("status", "password_changed");
    }
}
