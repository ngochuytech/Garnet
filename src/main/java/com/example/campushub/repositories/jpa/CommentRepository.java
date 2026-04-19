package com.example.campushub.repositories.jpa;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.campushub.models.jpa.Comment;

public interface CommentRepository extends JpaRepository<Comment, String>{
    
    // Load n comment đầu tiên của một bài viết (mới nhất) và chỉ lấy comment gốc (không lấy reply)
    List<Comment> findByPostIdAndParentCommentIsNullOrderByCreatedAtDesc(String postId, Pageable pageable);

    // Load n comment cũ hơn một khoảng thời gian (dựa vào comment cuối cùng đang hiển thị)
    List<Comment> findByPostIdAndParentCommentIsNullAndCreatedAtLessThanOrderByCreatedAtDesc(String postId, LocalDateTime createdAt, Pageable pageable);

    List<Comment> findByParentComment_IdOrderByCreatedAtAsc(String parentId, Pageable pageable);

    List<Comment> findByParentComment_IdAndCreatedAtGreaterThanOrderByCreatedAtAsc(String parentId, LocalDateTime createdAt, Pageable pageable);

    @Modifying
    @Query("UPDATE Comment c SET c.replyCount = c.replyCount + 1 WHERE c.id = :commentId")
    void incrementReplyCount(@Param("commentId") String commentId);
}
