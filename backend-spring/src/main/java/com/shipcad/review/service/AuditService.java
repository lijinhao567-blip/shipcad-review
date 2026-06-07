package com.shipcad.review.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipcad.review.domain.AuditLog;
import com.shipcad.review.repo.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditLogRepository logs;
    private final ObjectMapper mapper;

    public AuditService(AuditLogRepository logs, ObjectMapper mapper) {
        this.logs = logs;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actor, String action, String targetType, String targetId, Object detail) {
        AuditLog log = new AuditLog();
        log.id = Ids.next("audit");
        log.actor = actor;
        log.action = action;
        log.targetType = targetType;
        log.targetId = targetId;
        log.detailJson = toJson(detail);
        log.createdAt = Ids.now();
        logs.save(log);
    }

    public Page<AuditLog> search(String actor, String action, String targetType, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 200));
        Specification<AuditLog> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            addEqualIgnoreCase(predicates, builder, root.get("actor"), actor);
            addEqualIgnoreCase(predicates, builder, root.get("action"), action);
            addEqualIgnoreCase(predicates, builder, root.get("targetType"), targetType);
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return logs.findAll(
                specification,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }

    private void addEqualIgnoreCase(
            List<Predicate> predicates,
            jakarta.persistence.criteria.CriteriaBuilder builder,
            jakarta.persistence.criteria.Path<String> path,
            String value
    ) {
        if (value != null && !value.isBlank()) {
            predicates.add(builder.equal(builder.lower(path), value.trim().toLowerCase(java.util.Locale.ROOT)));
        }
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
