package com.shipcad.review.api;

import com.shipcad.review.dto.ApiDtos.LoginRequest;
import com.shipcad.review.dto.ApiDtos.LoginResponse;
import com.shipcad.review.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController extends BaseController {
    public AuthController(AuthService auth) {
        super(auth);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = auth.login(request.username(), request.password());
        return new LoginResponse(result.token(), view(result.user()));
    }
}
