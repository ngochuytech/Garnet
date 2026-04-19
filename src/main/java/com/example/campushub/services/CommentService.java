package com.example.campushub.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.campushub.enums.ReactionType;
import com.example.campushub.exceptions.DataNotFoundException;
import com.example.campushub.models.jpa.Comment;
import com.example.campushub.models.jpa.CommentReaction;
import com.example.campushub.models.jpa.CommentReactionId;
import com.example.campushub.models.jpa.Post;
import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.CommentReactionRepository;
import com.example.campushub.repositories.jpa.CommentRepository;
import com.example.campushub.repositories.jpa.PostRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository commentRepository;
    private final CommentReactionRepository commentReactionRepository;
    private final PostRepository postRepository;
    private final PostService postService;

    public List<Comment> getCommentsByPostId(String postId, String lastCommentId, int limit) throws Exception {
        List<Comment> comments;

        if (lastCommentId == null || lastCommentId.isEmpty()) {
            // Lần load đầu tiên (lấy các comment mới nhất)
            comments = commentRepository.findByPostIdAndParentCommentIsNullOrderByCreatedAtDesc(
                    postId, PageRequest.of(0, limit));
        } else {
            // Các lần load tiếp theo
            Comment lastComment = commentRepository.findById(lastCommentId)
                    .orElseThrow(() -> new DataNotFoundException("Khong tim thay comment cuoi cung"));
            comments = commentRepository.findByPostIdAndParentCommentIsNullAndCreatedAtLessThanOrderByCreatedAtDesc(
                    postId, lastComment.getCreatedAt(), PageRequest.of(0, limit));
        }

        return comments;
    }

    public List<Comment> getCommentReplies(String commentId, String lastCommentId, Integer limit) throws Exception{
        List<Comment> replies;
        if(lastCommentId == null){
            replies = commentRepository.findByParentComment_IdOrderByCreatedAtAsc(commentId, PageRequest.of(0 , limit));
        } else {
            Comment lastComment = commentRepository.findById(lastCommentId)
                .orElseThrow(() -> new DataNotFoundException("Bình luận không tồn tại"));
            replies = commentRepository.findByParentComment_IdAndCreatedAtGreaterThanOrderByCreatedAtAsc(commentId, lastComment.getCreatedAt(), PageRequest.of(0, limit));
        }
        return replies;
    }

    public Map<String, String> getUserReactionsMapForComments(User user, List<Comment> comments) {
        Map<String, String> userReactionsMap = new HashMap<>();
        if (user != null && comments != null && !comments.isEmpty()) {
            List<CommentReaction> reactions = commentReactionRepository.findByUserAndCommentIn(user, comments);
            for (CommentReaction r : reactions) {
                userReactionsMap.put(r.getComment().getId(), r.getType().name());
            }
        }
        return userReactionsMap;
    }

    @Transactional("transactionManager")
    public void createComment(User user, String postId, String parentId, String content) throws Exception {
        Post post = postService.getActivePostById(postId);

        Comment comment = Comment.builder()
                .post(post)
                .user(user)
                .content(content)
                .build();
        if (parentId != null) {
            Comment parentComment = commentRepository.findById(parentId)
                    .orElseThrow(() -> new DataNotFoundException("Không tìm thấy bình luận mà bạn muốn phản hồi"));
            commentRepository.incrementReplyCount(parentId);
            
            comment.setParentComment(parentComment);
        }
        postRepository.incrementCommentCount(postId);
        commentRepository.save(comment);
    }

    @Transactional("transactionManager")
    public void likeComment(User user, String commentId) throws Exception {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy bình luận"));
        CommentReaction commentReaction = commentReactionRepository.findByCommentAndUser(comment, user);

        if (commentReaction == null) {
            commentReaction = CommentReaction.builder()
                    .id(new CommentReactionId(comment.getId(), user.getId()))
                    .comment(comment)
                    .user(user)
                    .type(ReactionType.LIKE)
                    .build();
            comment.setLiked(comment.getLiked() + 1);
            commentRepository.save(comment);
            commentReactionRepository.save(commentReaction);
        } else if (commentReaction.getType() == ReactionType.DISLIKE) {
            comment.setDisliked(comment.getDisliked() - 1);
            comment.setLiked(comment.getLiked() + 1);
            commentRepository.save(comment);
            commentReaction.setType(ReactionType.LIKE);
            commentReactionRepository.save(commentReaction);
        } else {
            comment.setLiked(comment.getLiked() - 1);
            commentRepository.save(comment);
            commentReactionRepository.delete(commentReaction);
        }
    }

    @Transactional("transactionManager")
    public void dislikeComment(User user, String commentId) throws Exception {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy bình luận"));
        CommentReaction commentReaction = commentReactionRepository.findByCommentAndUser(comment, user);

        if (commentReaction == null) {
            commentReaction = CommentReaction.builder()
                    .id(new CommentReactionId(comment.getId(), user.getId()))
                    .comment(comment)
                    .user(user)
                    .type(ReactionType.DISLIKE)
                    .build();
            comment.setDisliked(comment.getDisliked() + 1);
            commentRepository.save(comment);
            commentReactionRepository.save(commentReaction);
        } else if (commentReaction.getType() == ReactionType.LIKE) {
            comment.setLiked(comment.getLiked() - 1);
            comment.setDisliked(comment.getDisliked() + 1);
            commentRepository.save(comment);
            commentReaction.setType(ReactionType.DISLIKE);
            commentReactionRepository.save(commentReaction);
        } else {
            comment.setDisliked(comment.getDisliked() - 1);
            commentRepository.save(comment);
            commentReactionRepository.delete(commentReaction);
        }
    }

    public String getUserReaction(User user, Comment comment) {
        CommentReaction reaction = commentReactionRepository.findByCommentAndUser(comment, user);
        if (reaction == null) {
            return "NONE";
        }
        return reaction.getType().name();
    }

    public Map<String, String> getUserReactionsMap(User user, String postId) {
        Map<String, String> userReactionsMap = new HashMap<>();
        if (user != null) {
            List<CommentReaction> reactions = commentReactionRepository.findByUserAndComment_Post_Id(user, postId);
            for (CommentReaction r : reactions) {
                userReactionsMap.put(r.getComment().getId(), r.getType().name());
            }
        }
        return userReactionsMap;
    }
}
