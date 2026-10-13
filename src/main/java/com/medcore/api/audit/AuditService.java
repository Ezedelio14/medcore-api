package com.medcore.api.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final JsonMapper jsonMapper;

    /** Audit writes run in their own transaction so they never roll back (or break) the audited operation. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID userId, String action, String entityType, UUID entityId, Map<String, ?> metadata) {
        try {
            String json = metadata == null || metadata.isEmpty() ? null : jsonMapper.writeValueAsString(metadata);
            auditLogRepository.save(new AuditLog(userId, action, entityType, entityId, json));
        } catch (RuntimeException e) {
            log.error("Failed to write audit log for action {}", action, e);
        }
    }
}
