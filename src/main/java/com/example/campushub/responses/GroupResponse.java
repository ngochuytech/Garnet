package com.example.campushub.responses;

import com.example.campushub.enums.GroupStatus;
import com.example.campushub.enums.MemberRole;
import com.example.campushub.enums.MemberStatus;
import com.example.campushub.models.jpa.Group;
import com.example.campushub.models.jpa.GroupMember;
import com.example.campushub.models.jpa.User;
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
    private String leaderName;
    private String leaderAvatarUrl;
    private MemberStatus memberStatus;
    private MemberRole memberRole;
    private Boolean isMember;
    private Boolean isPending;
    private Boolean isLeader;

    public static GroupResponse fromGroup(Group group) {
        return fromGroup(group, null);
    }

    public static GroupResponse fromGroup(Group group, GroupMember currentUserMember) {
        return fromGroup(group, currentUserMember, null);
    }

    public static GroupResponse fromGroup(Group group, GroupMember currentUserMember, GroupMember leaderMember) {
        MemberStatus currentMemberStatus = currentUserMember != null ? currentUserMember.getStatus() : null;
        MemberRole currentMemberRole = currentUserMember != null ? currentUserMember.getRole() : null;
        GroupMember resolvedLeaderMember = leaderMember != null ? leaderMember : (
                currentMemberRole == MemberRole.LEADER && currentMemberStatus == MemberStatus.APPROVED
                        ? currentUserMember
                        : null
        );
        User leader = resolvedLeaderMember != null ? resolvedLeaderMember.getUser() : null;

        return GroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .avatarUrl(group.getAvatarUrl())
                .coverUrl(group.getCoverUrl())
                .memberCount(group.getMemberCount())
                .status(group.getStatus())
                .createdAt(group.getCreatedAt())
                .leaderName(leader != null ? leader.getFullName() : null)
                .leaderAvatarUrl(leader != null ? leader.getAvatarUrl() : null)
                .memberStatus(currentMemberStatus)
                .memberRole(currentMemberRole)
                .isMember(currentMemberStatus == MemberStatus.APPROVED)
                .isPending(currentMemberStatus == MemberStatus.PENDING)
                .isLeader(currentMemberRole == MemberRole.LEADER && currentMemberStatus == MemberStatus.APPROVED)
                .build();
    }
}
