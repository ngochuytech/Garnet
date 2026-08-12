package com.example.campushub.repositories.jpa;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.campushub.enums.ContentStatus;
import com.example.campushub.models.jpa.Comment;
import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.projections.CommentReplyCountProjection;
import com.example.campushub.repositories.jpa.projections.PostCountProjection;

public interface CommentRepository extends JpaRepository<Comment, String> {
    Page<Comment> findByUser(User user, Pageable pageable);

    Optional<Comment> findFirstByUser_IdAndPost_IdAndStatusOrderByCreatedAtAsc(
            String userId, String postId, ContentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Comment c WHERE c.id = :commentId")
    Optional<Comment> findByIdForUpdate(@Param("commentId") String commentId);

    // Load n comment đầu tiên của một bài viết (mới nhất) và chỉ lấy comment gốc (Ko lấy reply)
    List<Comment> findByPostIdAndParentCommentIsNullAndStatusOrderByCreatedAtDesc(
            String postId, ContentStatus status, Pageable pageable);

    // Load n comment cũ hơn một khoảng thời gian (dựa vào comment cuối cùng đang hiển thị)
    List<Comment> findByPostIdAndParentCommentIsNullAndStatusAndCreatedAtLessThanOrderByCreatedAtDesc(
            String postId, ContentStatus status, LocalDateTime createdAt, Pageable pageable);

    List<Comment> findByParentComment_IdAndStatusOrderByCreatedAtAsc(
            String parentId, ContentStatus status, Pageable pageable);

    List<Comment> findByParentComment_IdAndStatus(String parentId, ContentStatus status);

    List<Comment> findByParentComment_IdAndStatusAndCreatedAtGreaterThanOrderByCreatedAtAsc(
            String parentId, ContentStatus status, LocalDateTime createdAt, Pageable pageable);

    @Query("SELECT c.parentComment.id AS commentId, COUNT(c) AS count " +
            "FROM Comment c " +
            "WHERE c.parentComment.id IN :commentIds AND c.status = :status " +
            "GROUP BY c.parentComment.id")
    List<CommentReplyCountProjection> countRepliesByCommentIdsAndStatus(
            @Param("commentIds") List<String> commentIds,
            @Param("status") ContentStatus status);

    long countByParentComment_IdAndStatus(String commentId, ContentStatus status);

    @Query("SELECT c.post.id AS postId, COUNT(c) AS count " +
            "FROM Comment c " +
            "WHERE c.post.id IN :postIds AND c.status = :status " +
            "GROUP BY c.post.id")
    List<PostCountProjection> countByPostIdsAndStatus(
            @Param("postIds") List<String> postIds,
            @Param("status") ContentStatus status);

    long countByPost_IdAndStatus(String postId, ContentStatus status);

    @Query("SELECT COUNT(c) FROM Comment c " +
            "WHERE c.createdAt >= :start AND c.createdAt < :end")
    long countCommentBetweenStartAndEnd(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
