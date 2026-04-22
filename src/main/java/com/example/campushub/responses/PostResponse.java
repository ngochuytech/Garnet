package com.example.campushub.responses;

import java.time.LocalDateTime;
import java.util.List;

import com.example.campushub.enums.ContentStatus;
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
    private Integer commentCount;
    private Integer shareCount;
    private String userReaction;
    private SharedPostResponse sharedPost;
    private List<String> images;
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

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class SharedPostResponse {
        private String id;
        private AuthorResponse author;
        private String content;
        private List<String> images;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    public static PostResponse fromPost(Post post) {
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
            .commentCount(post.getCommentCount())
            .shareCount(post.getSharedCount())
            .userReaction(userReaction)
            .images(post.getImages())
            .sharedPost(post.getSharedPost() != null ? SharedPostResponse.builder()
                .id(post.getSharedPost().getId())
                .content(post.getSharedPost().getStatus() == ContentStatus.ACTIVE ? post.getSharedPost().getContent() : "Nội dung này không còn khả dụng hoặc đã bị tác giả gỡ bỏ.")
                .images(post.getSharedPost().getStatus() == ContentStatus.ACTIVE ? post.getSharedPost().getImages() : List.of())
                .author(post.getSharedPost().getStatus() == ContentStatus.ACTIVE ? PostResponse.AuthorResponse.builder()
                    .id(post.getSharedPost().getUser().getId())
                    .authorName(post.getSharedPost().getUser().getFullName())
                    .authorAvatar(post.getSharedPost().getUser().getAvatarUrl())
                    .department(post.getSharedPost().getUser().getDepartment())
                    .build() : null)
                .createdAt(post.getSharedPost().getCreatedAt())
                .updatedAt(post.getSharedPost().getUpdatedAt())
                .build() : null
            )
            .createdAt(post.getCreatedAt())
            .updatedAt(post.getUpdatedAt())
            .build();
    }
}
