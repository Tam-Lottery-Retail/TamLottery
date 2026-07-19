package com.mtriet.tamlottery.audit.application;

import com.mtriet.tamlottery.audit.api.AuditDtos;
import com.mtriet.tamlottery.audit.domain.AuditAction;
import com.mtriet.tamlottery.audit.domain.AuditEntityType;
import com.mtriet.tamlottery.audit.domain.AuditLog;
import com.mtriet.tamlottery.audit.infrastructure.AuditLogRepository;
import com.mtriet.tamlottery.common.exception.BusinessException;
import com.mtriet.tamlottery.common.exception.ErrorCode;
import com.mtriet.tamlottery.identity.domain.Role;
import com.mtriet.tamlottery.identity.domain.UserAccount;
import com.mtriet.tamlottery.identity.infrastructure.UserAccountRepository;
import com.mtriet.tamlottery.identity.security.CurrentUser;
import com.mtriet.tamlottery.identity.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AuditService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogRepository auditLogRepository;
    private final UserAccountRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository,
                        UserAccountRepository userRepository,
                        CurrentUserProvider currentUserProvider,
                        ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(CurrentUser actor,
                       AuditAction action,
                       AuditEntityType entityType,
                       Object entityId,
                       LocalDate businessDate,
                       String reason,
                       Object beforeState,
                       Object afterState) {
        UserAccount account = userRepository.findByIdAndStoreId(actor.userId(), actor.storeId())
                .orElseThrow(() -> BusinessException.notFound("Audit actor not found"));
        record(account, action, entityType, entityId, businessDate, reason, beforeState, afterState);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UserAccount actor,
                       AuditAction action,
                       AuditEntityType entityType,
                       Object entityId,
                       LocalDate businessDate,
                       String reason,
                       Object beforeState,
                       Object afterState) {
        auditLogRepository.save(new AuditLog(
                actor.getStore().getId(),
                actor.getId(),
                actor.getUsername(),
                rolesText(actor.getRoles()),
                action,
                entityType,
                String.valueOf(entityId),
                businessDate,
                normalizeReason(reason),
                toJson(beforeState),
                toJson(afterState),
                MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY),
                currentIpAddress(),
                Instant.now()));
    }

    @Transactional(readOnly = true)
    public Page<AuditDtos.AuditLogResponse> search(AuditAction action,
                                                   AuditEntityType entityType,
                                                   String entityId,
                                                   Long actorUserId,
                                                   Instant fromTime,
                                                   Instant toTime,
                                                   Pageable pageable) {
        if (fromTime != null && toTime != null && !fromTime.isBefore(toTime)) {
            throw BusinessException.invalid(ErrorCode.INVALID_REQUEST, "fromTime must be before toTime");
        }
        CurrentUser current = currentUserProvider.get();
        Pageable bounded = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), MAX_PAGE_SIZE),
                pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "occurredAt"));
        String normalizedEntityId = entityId == null || entityId.isBlank() ? null : entityId.trim();
        return auditLogRepository.search(
                        current.storeId(), action, entityType, normalizedEntityId, actorUserId, fromTime, toTime, bounded)
                .map(this::toResponse);
    }

    private AuditDtos.AuditLogResponse toResponse(AuditLog auditLog) {
        List<String> roles = auditLog.getActorRoles().isBlank()
                ? List.of()
                : Arrays.asList(auditLog.getActorRoles().split(","));
        return new AuditDtos.AuditLogResponse(
                auditLog.getId(),
                auditLog.getActorUserId(),
                auditLog.getActorUsername(),
                roles,
                auditLog.getAction(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getBusinessDate(),
                auditLog.getReason(),
                fromJson(auditLog.getBeforeState()),
                fromJson(auditLog.getAfterState()),
                auditLog.getRequestId(),
                auditLog.getIpAddress(),
                auditLog.getOccurredAt());
    }

    private String rolesText(Set<Role> roles) {
        return roles.stream()
                .sorted(Comparator.comparing(Enum::name))
                .map(Role::name)
                .collect(Collectors.joining(","));
    }

    private String toJson(Object state) {
        if (state == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize audit state", exception);
        }
    }

    private JsonNode fromJson(String state) {
        if (state == null) {
            return null;
        }
        try {
            return objectMapper.readTree(state);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not deserialize audit state", exception);
        }
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String normalized = reason.trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private String currentIpAddress() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String remoteAddress = request.getRemoteAddr();
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return null;
        }
        return remoteAddress.length() <= 45 ? remoteAddress : remoteAddress.substring(0, 45);
    }
}
