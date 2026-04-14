package com.example.campushub.responses;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
public class PostResponse {
    private String id;
    private AuthorResponse author;
    private String content;
    private Integer likeCount;
    private Integer dislikeCount;
    private String userReaction;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class AuthorResponse {
        private String id;
        private String authorName;
        private String authorAvatar;
        private String department;

    }

    public static PostResponse fromPost(Post post){
        return fromPost(post, null);
    }

    public static PostResponse fromPost(Post post, String userReaction){
        return PostResponse.builder()
            .id(post.getId())
            .author(PostResponse.AuthorResponse.builder()
                .id(post.getUser().getId())
                .authorName(post.getUser().getFullName())
                .authorAvatar(post.getUser().getAvatarUrl())
                .department(post.getUser().getDepartment())
                .build())
            .content(post.getContent())
            .likeCount(post.getLiked())
            .dislikeCount(post.getDisliked())
            .userReaction(userReaction)
            .createdAt(post.getCreatedAt())
            .updatedAt(post.getUpdatedAt())
            .build();
    }
}
