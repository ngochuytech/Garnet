package com.example.campushub.repositories.jpa.projections;

public interface CommentReplyCountProjection {
    String getCommentId();

    Long getCount();
}
