package com.example.campushub.dtos.record.users;

import java.time.LocalDateTime;

public record UserFollowPayload(
    String followerId,
    String targetId,
    LocalDateTime createdAt
) {
    
}
