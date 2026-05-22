package com.shipcad.review.api;

import com.shipcad.review.domain.AppUser;
import com.shipcad.review.dto.ApiDtos.UserView;
import com.shipcad.review.service.AuthService;

public abstract class BaseController {
    protected final AuthService auth;

    protected BaseController(AuthService auth) {
        this.auth = auth;
    }

    protected AppUser user(String authorization) {
        return auth.requireUser(authorization);
    }

    protected UserView view(AppUser user) {
        return new UserView(user.id, user.username, user.displayName, user.role.name());
    }
}
