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
    private List<String> tags;
    private String groupId;
    private String groupName;
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
        private String groupId;
        private String groupName;
        private List<String> images;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    public static PostResponse fromPost(Post post) {
        return fromPost(post, null, null, null);
    }

    public static PostResponse fromPost(Post post, String userReaction) {
        return fromPost(post, userReaction, null, null);
    }

    public static PostResponse fromPost(Post post, String userReaction, List<String> tags) {
        return fromPost(post, userReaction, tags, null);
    }

    public static PostResponse fromPost(Post post, String userReaction, List<String> tags, List<String> sharedTags){
        return fromPost(post, userReaction, tags, sharedTags, null, null);
    }

    public static PostResponse fromPost(Post post, String userReaction, List<String> tags, List<String> sharedTags,
            String groupName, String sharedGroupName){
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
            .tags(tags)
            .groupId(post.getGroupId())
            .groupName(groupName)
            .images(post.getImages())
            .sharedPost(post.getSharedPost() != null ? SharedPostResponse.builder()
                .id(post.getSharedPost().getId())
                .content(post.getSharedPost().getStatus() == ContentStatus.ACTIVE ? post.getSharedPost().getContent() : "Nội dung này không còn khả dụng hoặc đã bị tác giả gỡ bỏ.")
                .tags(sharedTags)
                .groupId(post.getSharedPost().getGroupId())
                .groupName(sharedGroupName)
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
