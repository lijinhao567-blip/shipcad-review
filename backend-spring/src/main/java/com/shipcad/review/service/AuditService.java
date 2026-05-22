package com.shipcad.review.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipcad.review.domain.AuditLog;
import com.shipcad.review.repo.AuditLogRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditLogRepository logs;
    private final ObjectMapper mapper;

    public AuditService(AuditLogRepository logs, ObjectMapper mapper) {
        this.logs = logs;
        this.mapper = mapper;
    }

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

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
