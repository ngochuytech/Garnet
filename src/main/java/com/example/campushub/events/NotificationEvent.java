package com.example.campushub.events;

import com.example.campushub.enums.NotificationType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class NotificationEvent {
    private String actorId;
    private String recipientId;
    private String recipientName;
    private NotificationType type;
    private String targetType;
    private String targetId;
    private String message;
}
