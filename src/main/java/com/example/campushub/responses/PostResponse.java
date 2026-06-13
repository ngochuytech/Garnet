package com.example.campushub.responses;

import java.time.LocalDateTime;
import java.util.List;

import com.example.campushub.dtos.record.posts.PostStats;
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
        return fromPost(post, null, null, null, null, null, PostStats.empty());
    }

    public static PostResponse fromPost(Post post, String userReaction) {
        return fromPost(post, userReaction, null, null, null, null, PostStats.empty());
    }

    public static PostResponse fromPost(Post post, String userReaction, List<String> tags) {
        return fromPost(post, userReaction, tags, null, null, null, PostStats.empty());
    }

    public static PostResponse fromPost(Post post, String userReaction, List<String> tags, List<String> sharedTags){
        return fromPost(post, userReaction, tags, sharedTags, null, null, PostStats.empty());
    }

    public static PostResponse fromPost(Post post, String userReaction, List<String> tags, List<String> sharedTags,
            String groupName, String sharedGroupName){
        return fromPost(post, userReaction, tags, sharedTags, groupName, sharedGroupName, PostStats.empty());
    }

    public static PostResponse fromPost(Post post, String userReaction, List<String> tags, List<String> sharedTags,
            String groupName, String sharedGroupName, PostStats stats){
        Post sharedPost = post.getSharedPost();
        return PostResponse.builder()
            .id(post.getId())
            .author(PostResponse.AuthorResponse.builder()
                .id(post.getUser().getId())
                .authorName(post.getUser().getFullName())
                .authorAvatar(post.getUser().getAvatarUrl())
                .department(post.getUser().getDepartment())
                .build())
            .content(post.getContent())
            .likeCount(stats.likeCount())
            .dislikeCount(stats.dislikeCount())
            .commentCount(stats.commentCount())
            .shareCount(stats.shareCount())
            .userReaction(userReaction)
            .tags(tags)
            .groupId(post.getGroup() != null ? post.getGroup().getId() : null)
            .groupName(groupName)
            .images(post.getImages())
            .sharedPost(sharedPost != null ? SharedPostResponse.builder()
                .id(sharedPost.getId())
                .content(post.getSharedPost().getStatus() == ContentStatus.ACTIVE ? post.getSharedPost().getContent() : "Nội dung này không còn khả dụng hoặc đã bị tác giả gỡ bỏ.")
                .tags(sharedTags)
                .groupId(sharedPost.getGroup() != null ? sharedPost.getGroup().getId() : null)
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
