package com.example.campushub.models.jpa;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_blocks", indexes = {
        @Index(name = "idx_user_blocks_blocked", columnList = "blocked_user_id")
})
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserBlock {
    @EmbeddedId
    private UserBlockId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("blockerUserId")
    @JoinColumn(name = "blocker_user_id", nullable = false)
    private User blocker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("blockedUserId")
    @JoinColumn(name = "blocked_user_id", nullable = false)
    private User blocked;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
