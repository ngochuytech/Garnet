package com.example.campushub.repositories.jpa;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.campushub.enums.ContentStatus;
import com.example.campushub.models.jpa.Comment;
import com.example.campushub.models.jpa.User;

public interface CommentRepository extends JpaRepository<Comment, String> {
    Page<Comment> findByUser(User user, Pageable pageable);

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

    @Modifying
    @Query("UPDATE Comment c SET c.replyCount = c.replyCount + 1 WHERE c.id = :commentId")
    void incrementReplyCount(@Param("commentId") String commentId);

    @Query("SELECT COUNT(c) FROM Comment c " +
            "WHERE c.createdAt >= :start AND c.createdAt < :end")
    long countCommentBetweenStartAndEnd(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
