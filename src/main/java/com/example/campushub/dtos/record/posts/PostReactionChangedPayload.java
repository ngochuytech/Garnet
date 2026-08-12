package com.example.campushub.dtos.record.posts;

import java.time.LocalDateTime;

public record PostReactionChangedPayload(
    String userId,
    String postId,
    LocalDateTime createdAt
) {
}
