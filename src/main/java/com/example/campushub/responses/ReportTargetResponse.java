package com.example.campushub.responses;

import java.time.LocalDateTime;
import java.util.List;

import com.example.campushub.enums.ReportType;
import com.example.campushub.models.jpa.Comment;
import com.example.campushub.models.jpa.Group;
import com.example.campushub.models.jpa.Post;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReportTargetResponse {
    private String id;
    private ReportType type;
    private String content;
    private String name;
    private String description;
    private List<String> images;
    private String avatarUrl;
    private String coverUrl;
    private Integer memberCount;
    private String status;
    private String postId;
    private String groupId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ReportTargetResponse fromPost(Post post, List<String> images) {
        return ReportTargetResponse.builder()
                .id(post.getId())
                .type(ReportType.POST)
                .content(post.getContent())
                .images(images != null ? images : List.of())
                .status(post.getStatus().name())
                .groupId(post.getGroup() != null ? post.getGroup().getId() : null)
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }

    public static ReportTargetResponse fromComment(Comment comment) {
        return ReportTargetResponse.builder()
                .id(comment.getId())
                .type(ReportType.COMMENT)
                .content(comment.getContent())
                .status(comment.getStatus().name())
                .postId(comment.getPost().getId())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }

    public static ReportTargetResponse fromGroup(Group group) {
        return ReportTargetResponse.builder()
                .id(group.getId())
                .type(ReportType.GROUP)
                .name(group.getName())
                .description(group.getDescription())
                .avatarUrl(group.getAvatarUrl())
                .coverUrl(group.getCoverUrl())
                .memberCount(group.getMemberCount())
                .status(group.getStatus().name())
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .build();
    }
}
