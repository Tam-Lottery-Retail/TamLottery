package com.mtriet.tamlottery.audit.api;

import com.mtriet.tamlottery.audit.domain.AuditAction;
import com.mtriet.tamlottery.audit.domain.AuditEntityType;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AuditDtos {
    private AuditDtos() {
    }

    public record AuditLogResponse(
            Long id,
            Long actorUserId,
            String actorUsername,
            List<String> actorRoles,
            AuditAction action,
            AuditEntityType entityType,
            String entityId,
            LocalDate businessDate,
            String reason,
            JsonNode beforeState,
            JsonNode afterState,
            String requestId,
            String ipAddress,
            Instant occurredAt) {
    }
}
