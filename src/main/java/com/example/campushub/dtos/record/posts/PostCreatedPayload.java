package com.example.campushub.dtos.record.posts;

import java.time.LocalDateTime;
import java.util.Set;

public record PostCreatedPayload(
    String postId,
    String authorId,
    String groupId,
    Set<String> tagNames,
    LocalDateTime createdAt
) {
    
}
