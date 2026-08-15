package com.example.campushub.models.jpa;

import java.time.LocalDateTime;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Durable MySQL work queue for synchronizing recommendation data with Chroma.
 */
@Entity
@Table(name = "recommendation_outbox",
        indexes = @Index(name = "idx_rec_outbox_status_created", columnList = "status,created_at"))
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RecommendationOutbox {

    public enum EventType {
        POST_UPSERT, // Upsert the post into ChromaDB when status post = active
        POST_INVALIDATE, // Remove the post from ChromaDB when status post != active
        USER_PROFILE_CHANGED, // Rebuild profile vector when user change profile
        USER_INTERACTION // Read payload interaction (like/dislike/impression/...) then update positive/negative user vecotrs in Ch
    }

    public enum Status {
        PENDING,
        PROCESSING,
        DONE,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    @Column(name = "aggregate_id", nullable = false, length = 255)
    private String aggregateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "JSON")
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'PENDING'")
    private Status status = Status.PENDING;

    @Builder.Default
    @Column(name = "retry_count", nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer retryCount = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)")
    private LocalDateTime updatedAt;

    @Column(name = "processed_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime processedAt;

    public static RecommendationOutbox create(EventType eventType, String aggregateId, Map<String, Object> payload) {
        return RecommendationOutbox.builder()
                .eventType(eventType)
                .aggregateId(aggregateId)
                .payload(payload)
                .status(Status.PENDING)
                .build();
    }
}
