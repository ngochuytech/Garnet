package com.example.campushub.dtos.record.groups;

public record GroupCreatedPayload(
    String groupId,
    String leaderId,
    String groupName
) {
    
}
