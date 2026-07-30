package com.example.campushub.responses;

import java.time.LocalDateTime;

import com.example.campushub.models.jpa.Message;
import com.example.campushub.models.jpa.User;

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
public class ConversationResponse {
    private String id;
    private UserResponse user;
    private String lastMessage;
    private LocalDateTime lastTimeMessage;
    private Boolean isRead;

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

    public static ConversationResponse fromMessage(Message message, String currentUserId) {
        boolean isCurrentUserSender = message.getSender().getId().equals(currentUserId);
        User other = isCurrentUserSender 
                     ? message.getReceiver() 
                     : message.getSender();
                     
        return ConversationResponse.builder()
                .id(message.getId())
                .user(UserResponse.builder()
                        .id(other.getId())
                        .name(other.getFullName())
                        .avatar(other.getAvatarUrl())
                        .build())
                .lastMessage(message.getContent())
                .lastTimeMessage(message.getCreatedAt())
                .isRead(isCurrentUserSender ? true : (message.getIsRead() != null ? message.getIsRead() : false))
                .build();
    }
}
