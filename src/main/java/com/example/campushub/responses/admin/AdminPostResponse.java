package com.example.campushub.responses.admin;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
public class AdminPostResponse {
    private String id;
    private AuthorResponse author;
    private String content;
    private Integer likeCount;
    private Integer dislikeCount;
    private Integer commentCount;
    private Integer shareCount;
    private String status;
    private List<String> tags;
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
        private List<String> tags;
        private List<String> images;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    public static AdminPostResponse fromEntity(Post post, Map<String, List<String>> tagsMap) {
        List<String> tags = tagsMap != null ? tagsMap.get(post.getId()) : null;
        List<String> sharedTags = post.getSharedPost() != null && tagsMap != null
                ? tagsMap.get(post.getSharedPost().getId())
                : null;

        return AdminPostResponse.builder()
                .id(post.getId())
                .author(AuthorResponse.builder()
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
                .status(post.getStatus().name())
                .tags(tags)
                .images(post.getImages())
                .sharedPost(post.getSharedPost() != null ? SharedPostResponse.builder()
                        .id(post.getSharedPost().getId())
                        .author(post.getSharedPost().getUser() != null ? AuthorResponse.builder()
                                .id(post.getSharedPost().getUser().getId())
                                .authorName(post.getSharedPost().getUser().getFullName())
                                .authorAvatar(post.getSharedPost().getUser().getAvatarUrl())
                                .department(post.getSharedPost().getUser().getDepartment())
                                .build() : null)
                        .content(post.getSharedPost().getContent())
                        .tags(sharedTags)
                        .images(post.getSharedPost().getImages())
                        .createdAt(post.getSharedPost().getCreatedAt())
                        .updatedAt(post.getSharedPost().getUpdatedAt())
                        .build() : null)
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }
}