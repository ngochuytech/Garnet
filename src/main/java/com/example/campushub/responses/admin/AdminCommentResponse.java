package com.example.campushub.responses.admin;

import java.time.LocalDateTime;

import com.example.campushub.models.jpa.Comment;

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
public class AdminCommentResponse {
    private String id;
    private String postId;
    private String content;
    private Integer likeCount;
    private Integer dislikeCount;
    private String status;
    private UserResponse user;
    private Integer replyCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class UserResponse {
        private String id;
        private String name;
        private String avatar;
        private String department;
    }

    public static AdminCommentResponse fromEntity(Comment comment) {
        return AdminCommentResponse.builder()
                .id(comment.getId())
                .postId(comment.getPost().getId())
                .content(comment.getContent())
                .likeCount(comment.getLiked())
                .dislikeCount(comment.getDisliked())
                .status(comment.getStatus().name())
                .user(UserResponse.builder()
                        .id(comment.getUser().getId())
                        .name(comment.getUser().getFullName())
                        .avatar(comment.getUser().getAvatarUrl())
                        .department(comment.getUser().getDepartment())
                        .build())
                .replyCount(comment.getReplyCount())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}