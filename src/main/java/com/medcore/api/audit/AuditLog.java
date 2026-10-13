package com.medcore.api.audit;

import com.medcore.api.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog extends BaseEntity {

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "entity_type", length = 64)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    /** Serialized JSON stored in a JSONB column. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AuditLog(UUID userId, String action, String entityType, UUID entityId, String metadata) {
        this.userId = userId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.metadata = metadata;
    }
}
