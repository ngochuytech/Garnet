package com.example.campushub.repositories.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.campushub.models.jpa.RecommendationOutbox;

public interface RecommendationOutboxRepository extends JpaRepository<RecommendationOutbox, String> {
    @Query(value = """
            SELECT COUNT(*)
            FROM recommendation_outbox
            WHERE aggregate_id = :userId
              AND event_type = 'USER_INTERACTION'
              AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.post_id')) = :postId
              AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.action')) = :action
              AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.operation')) = 'ADD'
            """, nativeQuery = true)
    long countAddedUserInteractionsByPostAndAction(
            @Param("userId") String userId,
            @Param("postId") String postId,
            @Param("action") String action);
}
