package com.shipcad.review.config;

import com.shipcad.review.domain.AppUser;
import com.shipcad.review.domain.ReviewRule;
import com.shipcad.review.domain.Severity;
import com.shipcad.review.domain.UserRole;
import com.shipcad.review.repo.AppUserRepository;
import com.shipcad.review.repo.ReviewRuleRepository;
import com.shipcad.review.service.AuthService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer {
    private final AppUserRepository users;
    private final ReviewRuleRepository rules;
    private final AuthService auth;

    public DataInitializer(AppUserRepository users, ReviewRuleRepository rules, AuthService auth) {
        this.users = users;
        this.rules = rules;
        this.auth = auth;
    }

    @PostConstruct
    public void init() {
        if (users.findByUsername("admin").isEmpty()) {
            AppUser user = new AppUser();
            user.id = "user_admin";
            user.username = "admin";
            user.displayName = "系统管理员";
            user.role = UserRole.ADMIN;
            user.passwordHash = auth.encode("admin123");
            users.save(user);
        }
        if (rules.count() == 0) {
            addRule("LAYER_NAME_STANDARD", "图层命名规范检查", "检查图层是否使用推荐前缀。", Severity.MEDIUM);
            addRule("EMPTY_LAYER_CHECK", "空图层检查", "发现没有任何实体的图层。", Severity.LOW);
            addRule("TITLE_BLOCK_REQUIRED", "标题栏检查", "检查是否存在标题栏块或图号/版次文字。", Severity.HIGH);
            addRule("VERSION_FORMAT", "版次格式检查", "检查版次是否符合可追溯格式。", Severity.MEDIUM);
            addRule("TEXT_PLACEHOLDER", "占位文本检查", "检查 TBD、待定、XXX 等未完成标注。", Severity.MEDIUM);
            addRule("ENTITY_DENSITY", "实体数量异常检查", "检查图纸实体数量是否异常偏少。", Severity.HIGH);
        }
    }

    private void addRule(String code, String name, String description, Severity severity) {
        ReviewRule rule = new ReviewRule();
        rule.id = "rule_" + code.toLowerCase();
        rule.code = code;
        rule.name = name;
        rule.description = description;
        rule.severity = severity;
        rule.enabled = true;
        rules.save(rule);
    }
}
