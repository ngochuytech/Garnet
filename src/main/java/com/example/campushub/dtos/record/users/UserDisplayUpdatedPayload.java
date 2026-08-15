package com.example.campushub.dtos.record.users;

public record UserDisplayUpdatedPayload(
        String userId,
        String fullName,
        String avatarUrl) {
}
