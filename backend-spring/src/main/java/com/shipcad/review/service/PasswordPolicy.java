package com.shipcad.review.service;

import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {
    public void validate(String password) {
        if (password == null || password.length() < 10) {
            throw new IllegalArgumentException("密码至少需要10个字符");
        }
        if (password.length() > 128) {
            throw new IllegalArgumentException("密码不能超过128个字符");
        }
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) {
            throw new IllegalArgumentException("密码必须同时包含字母和数字");
        }
    }
}
