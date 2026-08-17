package com.example.campushub.repositories.jpa;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.campushub.enums.ContentStatus;
import com.example.campushub.enums.UserStatus;
import com.example.campushub.models.jpa.Post;
import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.projections.PostCountProjection;
import com.example.campushub.repositories.jpa.projections.PostMediaProjection;

public interface PostRepository extends JpaRepository<Post, String> {
        List<Post> findByUser(User user);

        Page<Post> findByUser(User user, Pageable pageable);

        List<Post> findByUserAndStatus(User user, ContentStatus status);

        @Query("SELECT p FROM Post p " +
                        "WHERE p.user.id = :userId " +
                        "AND p.status = :status " +
                        "AND p.user.status = :authorStatus " +
                        "ORDER BY p.createdAt DESC, p.id DESC")
        @EntityGraph(attributePaths = { "user", "group", "sharedPost", "sharedPost.user", "sharedPost.group" })
        List<Post> findLatestPostsByUserId(
                        @Param("userId") String userId,
                        @Param("status") ContentStatus status,
                        @Param("authorStatus") UserStatus authorStatus,
                        Pageable pageable);

        @Query("SELECT p FROM Post p " +
                        "WHERE p.user.id = :userId " +
                        "AND p.status = :status " +
                        "AND p.user.status = :authorStatus " +
                        "AND (p.createdAt < :cursorCreatedAt " +
                        "OR (p.createdAt = :cursorCreatedAt AND p.id < :cursorPostId)) " +
                        "ORDER BY p.createdAt DESC, p.id DESC")
        @EntityGraph(attributePaths = { "user", "group", "sharedPost", "sharedPost.user", "sharedPost.group" })
        List<Post> findLatestPostsByUserIdAfter(
                        @Param("userId") String userId,
                        @Param("status") ContentStatus status,
                        @Param("authorStatus") UserStatus authorStatus,
                        @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
                        @Param("cursorPostId") String cursorPostId,
                        Pageable pageable);

        Page<Post> findByStatus(ContentStatus status, Pageable pageable);

        @Query("SELECT p FROM Post p " +
                        "WHERE p.group.id = :groupId " +
                        "AND p.status = :status " +
                        "AND (p.user.status IS NULL OR p.user.status = :authorStatus) " +
                        "ORDER BY p.createdAt DESC, p.id DESC")
        @EntityGraph(attributePaths = { "user", "group", "sharedPost", "sharedPost.user", "sharedPost.group" })
        List<Post> findLatestPostsByGroupId(
                        @Param("groupId") String groupId,
                        @Param("status") ContentStatus status,
                        @Param("authorStatus") UserStatus authorStatus,
                        Pageable pageable);

        @Query("SELECT p FROM Post p " +
                        "WHERE p.group.id = :groupId " +
                        "AND p.status = :status " +
                        "AND (p.user.status IS NULL OR p.user.status = :authorStatus) " +
                        "AND (p.createdAt < :cursorCreatedAt " +
                        "OR (p.createdAt = :cursorCreatedAt AND p.id < :cursorPostId)) " +
                        "ORDER BY p.createdAt DESC, p.id DESC")
        @EntityGraph(attributePaths = { "user", "group", "sharedPost", "sharedPost.user", "sharedPost.group" })
        List<Post> findLatestPostsByGroupIdAfter(
                        @Param("groupId") String groupId,
                        @Param("status") ContentStatus status,
                        @Param("authorStatus") UserStatus authorStatus,
                        @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
                        @Param("cursorPostId") String cursorPostId,
                        Pageable pageable);

        @Query("SELECT p FROM Post p " +
                        "WHERE p.id IN :postIds " +
                        "AND p.group.id = :groupId " +
                        "AND p.status = :status " +
                        "AND (p.user.status IS NULL OR p.user.status = :authorStatus)")
        @EntityGraph(attributePaths = { "user", "group", "sharedPost", "sharedPost.user", "sharedPost.group" })
        List<Post> findActivePostsByIdsAndGroupId(
                        @Param("postIds") List<String> postIds,
                        @Param("groupId") String groupId,
                        @Param("status") ContentStatus status,
                        @Param("authorStatus") UserStatus authorStatus);

        Optional<Post> findByIdAndStatus(String id, ContentStatus status);

        boolean existsByUser_IdAndSharedPost_IdAndStatus(String userId, String sharedPostId, ContentStatus status);

        @Query("SELECT COUNT(*) FROM Post p " +
                        "WHERE p.createdAt >= :start AND p.createdAt < :end")
        long countPostBetweenStartAndEnd(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

        List<Post> findByIdInAndStatus(List<String> ids, ContentStatus status);

        @Query("SELECT p FROM Post p " +
                        "WHERE p.id IN :postIds " +
                        "AND p.status = :postStatus " +
                        "AND (p.user.status IS NULL OR p.user.status = :authorStatus)")
        @EntityGraph(attributePaths = { "user", "group", "sharedPost", "sharedPost.user", "sharedPost.group" })
        List<Post> findVisiblePostsByIds(
                        @Param("postIds") List<String> postIds,
                        @Param("postStatus") ContentStatus postStatus,
                        @Param("authorStatus") UserStatus authorStatus);

        @Query("SELECT p FROM Post p " +
                        "WHERE p.id IN :postIds " +
                        "AND p.status = :postStatus " +
                        "AND (p.user.status IS NULL OR p.user.status = :authorStatus) " +
                        "AND EXISTS (SELECT 1 FROM PostTag tag " +
                        "WHERE tag.post.id = p.id AND tag.id.tagName = :topicName)")
        @EntityGraph(attributePaths = { "user", "group", "sharedPost", "sharedPost.user", "sharedPost.group" })
        List<Post> findVisiblePostsByIdsAndTopicName(
                        @Param("postIds") List<String> postIds,
                        @Param("topicName") String topicName,
                        @Param("postStatus") ContentStatus postStatus,
                        @Param("authorStatus") UserStatus authorStatus);

        @Query("SELECT p FROM Post p " +
                        "WHERE p.status = :postStatus " +
                        "AND (p.user.status IS NULL OR p.user.status = :authorStatus) " +
                        "AND (" +
                        "EXISTS (SELECT 1 FROM UserFollow follow " +
                        "WHERE follow.follower.id = :userId AND follow.target.id = p.user.id) " +
                        "OR EXISTS (SELECT 1 FROM PostTag tag " +
                        "WHERE tag.post.id = p.id " +
                        "AND tag.id.tagName IN (SELECT interest.id.interestName FROM UserInterest interest " +
                        "WHERE interest.user.id = :userId))" +
                        ") " +
                        "ORDER BY p.createdAt DESC, p.id DESC")
        @EntityGraph(attributePaths = { "user", "group", "sharedPost", "sharedPost.user", "sharedPost.group" })
        List<Post> findLatestHomeFeedPosts(
                        @Param("userId") String userId,
                        @Param("postStatus") ContentStatus postStatus,
                        @Param("authorStatus") UserStatus authorStatus,
                        Pageable pageable);

        @Query("SELECT p FROM Post p " +
                        "WHERE p.status = :postStatus " +
                        "AND (p.user.status IS NULL OR p.user.status = :authorStatus) " +
                        "AND (" +
                        "EXISTS (SELECT 1 FROM UserFollow follow " +
                        "WHERE follow.follower.id = :userId AND follow.target.id = p.user.id) " +
                        "OR EXISTS (SELECT 1 FROM PostTag tag " +
                        "WHERE tag.post.id = p.id " +
                        "AND tag.id.tagName IN (SELECT interest.id.interestName FROM UserInterest interest " +
                        "WHERE interest.user.id = :userId))" +
                        ") " +
                        "AND (p.createdAt < :cursorCreatedAt " +
                        "OR (p.createdAt = :cursorCreatedAt AND p.id < :cursorPostId)) " +
                        "ORDER BY p.createdAt DESC, p.id DESC")
        @EntityGraph(attributePaths = { "user", "group", "sharedPost", "sharedPost.user", "sharedPost.group" })
        List<Post> findLatestHomeFeedPostsAfter(
                        @Param("userId") String userId,
                        @Param("postStatus") ContentStatus postStatus,
                        @Param("authorStatus") UserStatus authorStatus,
                        @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
                        @Param("cursorPostId") String cursorPostId,
                        Pageable pageable);

        @Query("SELECT p FROM Post p " +
                        "WHERE p.status = :postStatus " +
                        "AND (p.user.status IS NULL OR p.user.status = :authorStatus) " +
                        "AND EXISTS (SELECT 1 FROM PostTag tag " +
                        "WHERE tag.post.id = p.id AND tag.id.tagName = :topicName) " +
                        "ORDER BY p.createdAt DESC, p.id DESC")
        @EntityGraph(attributePaths = { "user", "group", "sharedPost", "sharedPost.user", "sharedPost.group" })
        List<Post> findLatestPostsByTopicName(
                        @Param("topicName") String topicName,
                        @Param("postStatus") ContentStatus postStatus,
                        @Param("authorStatus") UserStatus authorStatus,
                        Pageable pageable);

        @Query("SELECT p FROM Post p " +
                        "WHERE p.status = :postStatus " +
                        "AND (p.user.status IS NULL OR p.user.status = :authorStatus) " +
                        "AND EXISTS (SELECT 1 FROM PostTag tag " +
                        "WHERE tag.post.id = p.id AND tag.id.tagName = :topicName) " +
                        "AND (p.createdAt < :cursorCreatedAt " +
                        "OR (p.createdAt = :cursorCreatedAt AND p.id < :cursorPostId)) " +
                        "ORDER BY p.createdAt DESC, p.id DESC")
        @EntityGraph(attributePaths = { "user", "group", "sharedPost", "sharedPost.user", "sharedPost.group" })
        List<Post> findLatestPostsByTopicNameAfter(
                        @Param("topicName") String topicName,
                        @Param("postStatus") ContentStatus postStatus,
                        @Param("authorStatus") UserStatus authorStatus,
                        @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
                        @Param("cursorPostId") String cursorPostId,
                        Pageable pageable);

        @Query("SELECT imageUrl FROM Post p JOIN p.images imageUrl WHERE p.id = :postId")
        List<String> findImageUrlsByPostId(@Param("postId") String postId);

        @Query("SELECT p.id AS postId, imageUrl AS url " +
                        "FROM Post p JOIN p.images imageUrl " +
                        "WHERE p.id IN :postIds")
        List<PostMediaProjection> findImageUrlsByPostIds(@Param("postIds") List<String> postIds);

        @Query("SELECT p.id AS postId, videoUrl AS url " +
                        "FROM Post p JOIN p.videos videoUrl " +
                        "WHERE p.id IN :postIds")
        List<PostMediaProjection> findVideoUrlsByPostIds(@Param("postIds") List<String> postIds);

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
