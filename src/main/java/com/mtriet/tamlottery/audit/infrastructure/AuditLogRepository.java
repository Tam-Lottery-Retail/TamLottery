package com.mtriet.tamlottery.audit.infrastructure;

import com.mtriet.tamlottery.audit.domain.AuditAction;
import com.mtriet.tamlottery.audit.domain.AuditEntityType;
import com.mtriet.tamlottery.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AuditLogRepository extends Repository<AuditLog, Long> {

    AuditLog save(AuditLog auditLog);

    @Query("""
            select a from AuditLog a
            where a.storeId = :storeId
              and (:action is null or a.action = :action)
              and (:entityType is null or a.entityType = :entityType)
              and (:entityId is null or a.entityId = :entityId)
              and (:actorUserId is null or a.actorUserId = :actorUserId)
              and (:fromTime is null or a.occurredAt >= :fromTime)
              and (:toTime is null or a.occurredAt < :toTime)
            """)
    Page<AuditLog> search(
            @Param("storeId") Long storeId,
            @Param("action") AuditAction action,
            @Param("entityType") AuditEntityType entityType,
            @Param("entityId") String entityId,
            @Param("actorUserId") Long actorUserId,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime,
            Pageable pageable);
}
