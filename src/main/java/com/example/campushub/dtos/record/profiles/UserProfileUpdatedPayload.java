package com.example.campushub.dtos.record.profiles;

import java.util.Set;

import com.example.campushub.enums.UserStatus;

public record UserProfileUpdatedPayload(
    String userId,
    String major,
    Set<String> hobbies,
    UserStatus status
) {
    
}
