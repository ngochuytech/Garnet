package com.example.campushub.responses;

import com.example.campushub.enums.GroupStatus;
import com.example.campushub.enums.MemberRole;
import com.example.campushub.enums.MemberStatus;
import com.example.campushub.models.jpa.Group;
import com.example.campushub.models.jpa.GroupMember;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class GroupResponse {
    private String id;
    private String name;
    private String description;
    private String avatarUrl;
    private String coverUrl;
    private Integer memberCount;
    private GroupStatus status;
    private LocalDateTime createdAt;
    private MemberStatus memberStatus;
    private MemberRole memberRole;
    private Boolean isMember;
    private Boolean isPending;
    private Boolean isLeader;

    public static GroupResponse fromGroup(Group group) {
        return fromGroup(group, null);
    }

    public static GroupResponse fromGroup(Group group, GroupMember currentUserMember) {
        MemberStatus currentMemberStatus = currentUserMember != null ? currentUserMember.getStatus() : null;
        MemberRole currentMemberRole = currentUserMember != null ? currentUserMember.getRole() : null;

        return GroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .avatarUrl(group.getAvatarUrl())
                .coverUrl(group.getCoverUrl())
                .memberCount(group.getMemberCount())
                .status(group.getStatus())
                .createdAt(group.getCreatedAt())
                .memberStatus(currentMemberStatus)
                .memberRole(currentMemberRole)
                .isMember(currentMemberStatus == MemberStatus.APPROVED)
                .isPending(currentMemberStatus == MemberStatus.PENDING)
                .isLeader(currentMemberRole == MemberRole.LEADER && currentMemberStatus == MemberStatus.APPROVED)
                .build();
    }
}
