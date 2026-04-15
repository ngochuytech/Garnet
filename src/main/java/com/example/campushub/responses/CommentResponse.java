package com.example.campushub.responses;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
public class CommentResponse {
    private String id;
    private String postId;
    private String content;
    private Integer likeCount;
    private Integer dislikeCount;
    private String status;
    private UserResponse user;
    private String userReaction;
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

    public static CommentResponse fromComment(Comment comment, Map<String, String> userReactionsMap) {
        String reactionStr = null;
        if (userReactionsMap != null && userReactionsMap.containsKey(comment.getId())) {
            reactionStr = userReactionsMap.get(comment.getId());
        }

        return CommentResponse.builder()
                .id(comment.getId())
                .postId(comment.getPost().getId())
                .content(comment.getContent())
                .likeCount(comment.getLiked())
                .dislikeCount(comment.getDisliked())
                .status(comment.getStatus().name())
                .user(CommentResponse.UserResponse.builder()
                        .id(comment.getUser().getId())
                        .name(comment.getUser().getFullName())
                        .avatar(comment.getUser().getAvatarUrl())
                        .department(comment.getUser().getDepartment())
                        .build())
                .userReaction(reactionStr)
                .replyCount(comment.getReplyCount())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}