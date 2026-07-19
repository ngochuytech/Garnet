package com.example.campushub.dtos.record.posts;

import java.time.LocalDateTime;
import java.util.Set;

public record PostSharedPayload (
    String sharedPostId,
    String sharerId,
    String originalPostId,
    Set<String> tagNames,
    LocalDateTime createdAt
){
    
}
