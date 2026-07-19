package com.example.campushub.models.jpa;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.example.campushub.enums.Neo4jEventStatus;
import com.example.campushub.enums.Neo4jEventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "neo4j_sync_events")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Neo4jSyncEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 100)
    private Neo4jEventType eventType;

    @Column(name = "aggregate_id")
    private String aggregateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "json", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private Neo4jEventStatus status;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public static Neo4jSyncEvent pending(Neo4jEventType eventType, String aggregateId, String payload) {
        return Neo4jSyncEvent.builder()
                .eventType(eventType)
                .aggregateId(aggregateId)
                .payload(payload)
                .status(Neo4jEventStatus.PENDING)
                .retryCount(0)
                .build();
    }

}
