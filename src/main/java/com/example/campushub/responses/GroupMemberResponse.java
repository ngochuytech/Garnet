package com.example.campushub.responses;

import com.example.campushub.enums.MemberRole;
import com.example.campushub.enums.MemberStatus;
import com.example.campushub.models.jpa.GroupMember;
import com.example.campushub.models.jpa.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class GroupMemberResponse {
    private String userId;
    private String fullName;
    private String avatarUrl;
    private String email;
    private String department;
    private MemberRole role;
    private MemberStatus status;
    private LocalDateTime joinedAt;

    public static GroupMemberResponse fromGroupMember(GroupMember groupMember) {
        User user = groupMember.getUser();

        return GroupMemberResponse.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .email(user.getEmail())
                .department(user.getDepartment())
                .role(groupMember.getRole())
                .status(groupMember.getStatus())
                .joinedAt(groupMember.getJoinedAt())
                .build();
    }
}
