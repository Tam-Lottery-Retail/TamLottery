package com.mtriet.tamlottery.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "actor_username", nullable = false, length = 80)
    private String actorUsername;

    @Column(name = "actor_roles", nullable = false, length = 100)
    private String actorRoles;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 40)
    private AuditEntityType entityType;

    @Column(name = "entity_id", nullable = false, length = 100)
    private String entityId;

    @Column(name = "business_date")
    private LocalDate businessDate;

    @Column(length = 500)
    private String reason;

    @Column(name = "before_state", columnDefinition = "LONGTEXT")
    private String beforeState;

    @Column(name = "after_state", columnDefinition = "LONGTEXT")
    private String afterState;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditLog() {
    }

    public AuditLog(Long storeId,
                    Long actorUserId,
                    String actorUsername,
                    String actorRoles,
                    AuditAction action,
                    AuditEntityType entityType,
                    String entityId,
                    LocalDate businessDate,
                    String reason,
                    String beforeState,
                    String afterState,
                    String requestId,
                    String ipAddress,
                    Instant occurredAt) {
        this.storeId = storeId;
        this.actorUserId = actorUserId;
        this.actorUsername = actorUsername;
        this.actorRoles = actorRoles;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.businessDate = businessDate;
        this.reason = reason;
        this.beforeState = beforeState;
        this.afterState = afterState;
        this.requestId = requestId;
        this.ipAddress = ipAddress;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public Long getStoreId() {
        return storeId;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getActorRoles() {
        return actorRoles;
    }

    public AuditAction getAction() {
        return action;
    }

    public AuditEntityType getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public String getReason() {
        return reason;
    }

    public String getBeforeState() {
        return beforeState;
    }

    public String getAfterState() {
        return afterState;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
