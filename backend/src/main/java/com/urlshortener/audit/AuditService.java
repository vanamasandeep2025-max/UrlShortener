package com.urlshortener.audit;

import com.urlshortener.entity.ActorType;
import com.urlshortener.entity.AuditLog;
import com.urlshortener.repository.AuditLogRepository;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes to the append-only audit_logs table. Runs in REQUIRES_NEW so an audit write
 * is never silently lost if the caller's transaction later rolls back for an unrelated
 * reason, and (for failure-path logging) so a rollback doesn't erase the audit trail
 * of the very failure being recorded.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(ActorType actorType, UUID actorUserId, String action, String entityType,
                     String entityId, Map<String, Object> details, String ipAddress) {
        AuditLog entry = AuditLog.builder()
            .actorType(actorType)
            .actorUserId(actorUserId)
            .action(action)
            .entityType(entityType)
            .entityId(entityId)
            .details(details)
            .ipAddress(ipAddress)
            .correlationId(MDC.get("requestId"))
            .build();
        try {
            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Audit failures must never break the primary business operation.
            log.error("Failed to persist audit log entry: action={}, entityType={}, entityId={}",
                action, entityType, entityId, e);
        }
    }
}
