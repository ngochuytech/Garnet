package com.example.campushub.repositories.jpa.projections;

import com.example.campushub.enums.ReactionType;

public interface PostReactionCountProjection {
    String getPostId();

    ReactionType getType();

    Long getCount();
}
