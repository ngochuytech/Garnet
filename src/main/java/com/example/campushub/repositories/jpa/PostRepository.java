package com.example.campushub.repositories.jpa;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.campushub.enums.ContentStatus;
import com.example.campushub.models.jpa.Post;
import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.projections.PostCountProjection;

public interface PostRepository extends JpaRepository<Post, String> {
        List<Post> findByUser(User user);

        Page<Post> findByUser(User user, Pageable pageable);

        List<Post> findByUserAndStatus(User user, ContentStatus status);

        @Query("SELECT p FROM Post p " +
                        "WHERE p.user.id = :userId " +
                        "AND p.status = :status " +
                        "ORDER BY p.createdAt DESC, p.id DESC")
        List<Post> findLatestPostsByUserId(
                        @Param("userId") String userId,
                        @Param("status") ContentStatus status,
                        Pageable pageable);

        @Query("SELECT p FROM Post p " +
                        "WHERE p.user.id = :userId " +
                        "AND p.status = :status " +
                        "AND (p.createdAt < :cursorCreatedAt " +
                        "OR (p.createdAt = :cursorCreatedAt AND p.id < :cursorPostId)) " +
                        "ORDER BY p.createdAt DESC, p.id DESC")
        List<Post> findLatestPostsByUserIdAfter(
                        @Param("userId") String userId,
                        @Param("status") ContentStatus status,
                        @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
                        @Param("cursorPostId") String cursorPostId,
                        Pageable pageable);

        Page<Post> findByStatus(ContentStatus status, Pageable pageable);

        @Query("SELECT p FROM Post p " +
                        "WHERE p.group.id = :groupId " +
                        "AND p.status = :status " +
                        "ORDER BY p.createdAt DESC, p.id DESC")
        List<Post> findLatestPostsByGroupId(
                        @Param("groupId") String groupId,
                        @Param("status") ContentStatus status,
                        Pageable pageable);

        @Query("SELECT p FROM Post p " +
                        "WHERE p.group.id = :groupId " +
                        "AND p.status = :status " +
                        "AND (p.createdAt < :cursorCreatedAt " +
                        "OR (p.createdAt = :cursorCreatedAt AND p.id < :cursorPostId)) " +
                        "ORDER BY p.createdAt DESC, p.id DESC")
        List<Post> findLatestPostsByGroupIdAfter(
                        @Param("groupId") String groupId,
                        @Param("status") ContentStatus status,
                        @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
                        @Param("cursorPostId") String cursorPostId,
                        Pageable pageable);

        Optional<Post> findByIdAndStatus(String id, ContentStatus status);

        @Query("SELECT COUNT(*) FROM Post p " +
                        "WHERE p.createdAt >= :start AND p.createdAt < :end")
        long countPostBetweenStartAndEnd(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

        List<Post> findByIdInAndStatus(List<String> ids, ContentStatus status);

        @Query("SELECT imageUrl FROM Post p JOIN p.images imageUrl WHERE p.id = :postId")
        List<String> findImageUrlsByPostId(@Param("postId") String postId);

        @Query("SELECT p.sharedPost.id AS postId, COUNT(p) AS count " +
                        "FROM Post p " +
                        "WHERE p.sharedPost.id IN :postIds AND p.status = :status " +
                        "GROUP BY p.sharedPost.id")
        List<PostCountProjection> countSharesByPostIdsAndStatus(
                        @Param("postIds") List<String> postIds,
                        @Param("status") ContentStatus status);

        long countBySharedPost_IdAndStatus(String postId, ContentStatus status);

        @Query("SELECT p FROM Post p " +
                        "WHERE (:query IS NULL " +
                        "OR p.id LIKE CONCAT('%', :query, '%') " +
                        "OR LOWER(p.content) LIKE LOWER(CONCAT('%', :query, '%')) " +
                        "OR LOWER(p.user.fullName) LIKE LOWER(CONCAT('%', :query, '%')) " +
                        "OR LOWER(p.user.email) LIKE LOWER(CONCAT('%', :query, '%'))) " +
                        "AND (:status IS NULL OR p.status = :status)")
        Page<Post> searchPosts(@Param("query") String query, @Param("status") ContentStatus status, Pageable pageable);
}
