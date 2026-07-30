package com.example.campushub.responses;

import java.time.LocalDateTime;

import com.example.campushub.models.jpa.Message;

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
public class MessageResponse {
    String id;
    UserResponse sender;
    UserResponse receiver;
    String content;
    Boolean isRead;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class UserResponse {
        private String id;
        private String name;
        private String avatar;
    }

    public static MessageResponse fromEntity(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .sender(UserResponse.builder()
                        .id(message.getSender().getId())
                        .name(message.getSender().getFullName())
                        .avatar(message.getSender().getAvatarUrl())
                        .build())
                .receiver(UserResponse.builder()
                        .id(message.getReceiver().getId())
                        .name(message.getReceiver().getFullName())
                        .avatar(message.getReceiver().getAvatarUrl())
                        .build())
                .content(message.getContent())
                .isRead(message.getIsRead())
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .build();
    }
}
