package com.example.campushub.responses;

import java.time.LocalDateTime;

import com.example.campushub.models.jpa.Notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NotificationResponse {
    private String id;
    private ActorResposne actor;
    private String type;
    private String targetType;
    private String targetId;
    private boolean isRead;
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ActorResposne {
        private String id;
        private String fullName;
        private String avatarUrl;
    }

    public static NotificationResponse fromEntity(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .actor(notification.getActor() != null ? ActorResposne.builder()
                        .id(notification.getActor().getId())
                        .fullName(notification.getActor().getFullName())
                        .avatarUrl(notification.getActor().getAvatarUrl())
                        .build() : null)
                .type(notification.getType().name())
                .targetType(notification.getTargetType())
                .targetId(notification.getTargetId())
                .isRead(notification.isRead())
                .message(notification.getMessage())
                .createdAt(notification.getCreatedAt())
                .updatedAt(notification.getUpdatedAt())
                .build();
    }
}
