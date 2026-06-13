package com.example.campushub.repositories.jpa;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.campushub.models.jpa.Post;
import com.example.campushub.models.jpa.PostReaction;
import com.example.campushub.models.jpa.PostReactionId;
import com.example.campushub.enums.ReactionType;
import com.example.campushub.repositories.jpa.projections.PostReactionCountProjection;

import com.example.campushub.models.jpa.User;

@Repository
public interface PostReactionRepository extends JpaRepository<PostReaction, PostReactionId> {
    PostReaction findByPostAndUser(Post post, User user);
    List<PostReaction> findByPostInAndUser(List<Post> posts, User user);
    void deleteByPostAndUser(Post post, User user);

    @Query("SELECT pr.post.id AS postId, pr.type AS type, COUNT(pr) AS count " +
            "FROM PostReaction pr " +
            "WHERE pr.post.id IN :postIds " +
            "GROUP BY pr.post.id, pr.type")
    List<PostReactionCountProjection> countByPostIdsGroupedByType(@Param("postIds") List<String> postIds);

    long countByPost_IdAndType(String postId, ReactionType type);

    @Query("SELECT COUNT(pr) FROM PostReaction pr " +
            "WHERE pr.createdAt >= :start AND pr.createdAt < :end")
    long countPostReactionBetweenStartAndEnd(@Param("start") LocalDateTime start,@Param("end") LocalDateTime end);
}
