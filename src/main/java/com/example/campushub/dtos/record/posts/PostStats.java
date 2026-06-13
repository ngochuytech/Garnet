package com.example.campushub.dtos.record.posts;

public record PostStats(
        int likeCount,
        int dislikeCount,
        int commentCount,
        int shareCount) {

    public static PostStats empty() {
        return new PostStats(0, 0, 0, 0);
    }
}
