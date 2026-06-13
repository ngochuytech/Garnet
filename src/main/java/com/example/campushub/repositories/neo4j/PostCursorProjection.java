package com.example.campushub.repositories.neo4j;

import java.time.LocalDateTime;

public interface PostCursorProjection {
    String getId();

    LocalDateTime getCreatedAt();
}
