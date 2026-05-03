package com.example.campushub.repositories.jpa;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.campushub.models.jpa.Comment;
import com.example.campushub.models.jpa.CommentReaction;
import com.example.campushub.models.jpa.CommentReactionId;
import com.example.campushub.models.jpa.User;

import java.util.List;

@Repository
public interface CommentReactionRepository extends JpaRepository<CommentReaction, CommentReactionId>{
    CommentReaction findByCommentAndUser(Comment comment, User user);
    
    List<CommentReaction> findByUserAndComment_Post_Id(User user, String postId);

    List<CommentReaction> findByUserAndCommentIn(User user, List<Comment> comments);

    @Query("SELECT COUNT(cr) FROM CommentReaction cr " +
            "WHERE cr.createdAt >= :start AND cr.createdAt < :end")
    long countCommentReactionBetweenStartAndEnd(@Param("start") LocalDateTime start,@Param("end") LocalDateTime end);

}
