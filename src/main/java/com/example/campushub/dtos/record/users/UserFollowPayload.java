package com.example.campushub.dtos.record.users;

public record UserFollowPayload(
    String followerId,
    String targetId
) {
    
}
