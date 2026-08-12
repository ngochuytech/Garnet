package com.example.campushub.dtos.record.users;

import com.example.campushub.enums.UserStatus;

public record UserStatusChangedPayload(String userId, UserStatus status) {
}
