package com.example.campushub.dtos.record.posts;

import com.example.campushub.enums.ContentStatus;

public record PostStatusChangedPayload(
    String postId,
    ContentStatus status
) {
    
}
