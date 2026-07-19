package com.mtriet.tamlottery.audit.api;

import com.mtriet.tamlottery.audit.application.AuditService;
import com.mtriet.tamlottery.audit.domain.AuditAction;
import com.mtriet.tamlottery.audit.domain.AuditEntityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    Page<AuditDtos.AuditLogResponse> search(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) AuditEntityType entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toTime,
            Pageable pageable) {
        return auditService.search(action, entityType, entityId, actorUserId, fromTime, toTime, pageable);
    }
}
