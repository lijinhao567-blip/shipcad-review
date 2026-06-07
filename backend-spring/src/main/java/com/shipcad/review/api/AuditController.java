package com.shipcad.review.api;

import com.shipcad.review.domain.AuditLog;
import com.shipcad.review.domain.Permission;
import com.shipcad.review.dto.ApiDtos.AuditLogPage;
import com.shipcad.review.dto.ApiDtos.AuditLogView;
import com.shipcad.review.service.AuditService;
import com.shipcad.review.service.AuthorizationService;
import com.shipcad.review.service.AuthService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditController extends BaseController {
    private final AuthorizationService access;
    private final AuditService audit;

    public AuditController(AuthService auth, AuthorizationService access, AuditService audit) {
        super(auth);
        this.access = access;
        this.audit = audit;
    }

    @GetMapping
    public AuditLogPage logs(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        access.require(user(authorization), Permission.AUDIT_VIEW);
        Page<AuditLog> result = audit.search(actor, action, targetType, page, size);
        return new AuditLogPage(
                result.getContent().stream().map(this::view).toList(),
                result.getTotalElements(),
                result.getNumber(),
                result.getSize(),
                result.getTotalPages()
        );
    }

    private AuditLogView view(AuditLog log) {
        return new AuditLogView(
                log.id,
                log.actor,
                log.action,
                log.targetType,
                log.targetId,
                log.detailJson,
                log.createdAt
        );
    }
}
