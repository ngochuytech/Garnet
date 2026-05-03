package com.example.campushub.repositories.jpa;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.campushub.enums.ContentStatus;
import com.example.campushub.models.jpa.Post;
import com.example.campushub.models.jpa.User;

@Repository
public interface PostRepository extends JpaRepository<Post, String> {
        List<Post> findByUser(User user);

        Page<Post> findByUser(User user, Pageable pageable);

        List<Post> findByUserAndStatus(User user, ContentStatus status);

        Page<Post> findByUserAndStatus(User user, ContentStatus status, Pageable pageable);

        Page<Post> findByStatus(ContentStatus status, Pageable pageable);

        Optional<Post> findByIdAndStatus(String id, ContentStatus status);

        @Query("SELECT COUNT(*) FROM Post p " +
                        "WHERE p.createdAt >= :start AND p.createdAt < :end")
        long countPostBetweenStartAndEnd(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

        @Query("SELECT COALESCE(SUM(COALESCE(p.sharedCount, 0)), 0) FROM Post p")
        Long sumSharedCount();

        List<Post> findByIdInAndStatus(List<String> ids, ContentStatus status);

        @Modifying
        @Query("UPDATE Post p SET p.commentCount = p.commentCount + 1 WHERE p.id = :postId")
        void incrementCommentCount(@Param("postId") String postId);

        @Query("SELECT p FROM Post p " +
                        "WHERE (:query IS NULL " +
                        "OR p.id LIKE CONCAT('%', :query, '%') " +
                        "OR LOWER(p.content) LIKE LOWER(CONCAT('%', :query, '%')) " +
                        "OR LOWER(p.user.fullName) LIKE LOWER(CONCAT('%', :query, '%')) " +
                        "OR LOWER(p.user.email) LIKE LOWER(CONCAT('%', :query, '%'))) " +
                        "AND (:status IS NULL OR p.status = :status)")
        Page<Post> searchPosts(@Param("query") String query, @Param("status") ContentStatus status, Pageable pageable);
}
