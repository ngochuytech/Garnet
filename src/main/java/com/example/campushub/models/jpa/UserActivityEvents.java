package com.example.campushub.models.jpa;

import java.time.LocalDateTime;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_activity_events", indexes = {
        @Index(name = "idx_activity_actor_time", columnList = "actor_user_id,event_type,occurred_at"),
        @Index(name = "idx_activity_type_time", columnList = "event_type,occurred_at"),
        @Index(name = "idx_activity_target_time", columnList = "target_type,target_id,occurred_at")
})
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserActivityEvents {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "actor_user_id", nullable = false, length = 255)
    private String actorUserId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    @Column(name = "target_id", nullable = false, length = 255)
    private String targetId;

    @Column(name = "occurred_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime occurredAt;

    @Column(name = "request_id", length = 255)
    private String requestId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "JSON")
    private Map<String, Object> metadata;

    @PrePersist
    protected void onCreate() {
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }
    }
}
