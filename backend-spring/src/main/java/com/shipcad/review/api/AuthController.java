package com.shipcad.review.api;

import com.shipcad.review.dto.ApiDtos.AccessView;
import com.shipcad.review.dto.ApiDtos.LoginRequest;
import com.shipcad.review.dto.ApiDtos.LoginResponse;
import com.shipcad.review.service.AuthorizationService;
import com.shipcad.review.service.AuthService;
import jakarta.validation.Valid;
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

    public AuthController(AuthService auth, AuthorizationService access) {
        super(auth);
        this.access = access;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = auth.login(request.username(), request.password());
        return new LoginResponse(result.token(), view(result.user()), access.permissions(result.user()));
    }

    @GetMapping("/me")
    public AccessView me(@RequestHeader("Authorization") String authorization) {
        var current = user(authorization);
        return new AccessView(view(current), access.permissions(current));
    }
}
