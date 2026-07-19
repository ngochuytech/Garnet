package com.example.campushub.dtos.record.profiles;

import java.util.Set;

public record UserProfileUpdatedPayload(
    String userId,
    String major,
    Set<String> hobbies
) {
    
}
