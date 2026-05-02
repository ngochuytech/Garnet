package com.example.campushub.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import com.example.campushub.enums.NotificationType;
import com.example.campushub.enums.ReactionType;
import com.example.campushub.events.NotificationEvent;
import com.example.campushub.exceptions.DataNotFoundException;
import com.example.campushub.models.jpa.Comment;
import com.example.campushub.models.jpa.CommentReaction;
import com.example.campushub.models.jpa.CommentReactionId;
import com.example.campushub.models.jpa.Post;
import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.CommentReactionRepository;
import com.example.campushub.repositories.jpa.CommentRepository;
import com.example.campushub.repositories.jpa.PostRepository;
import com.example.campushub.repositories.jpa.UserRepository;
import com.example.campushub.responses.admin.AdminCommentResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository commentRepository;
    private final CommentReactionRepository commentReactionRepository;
    private final PostRepository postRepository;
    private final PostService postService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public Slice<Comment> getCommentsByPostId(String postId, String lastCommentId, int limit) throws Exception {
        List<Comment> comments;
        int fetchSize = limit + 1;
        Pageable fetchPageable = PageRequest.of(0, fetchSize);

        if (lastCommentId == null || lastCommentId.isEmpty()) {
            // Lần load đầu tiên (lấy các comment mới nhất)
            comments = commentRepository.findByPostIdAndParentCommentIsNullOrderByCreatedAtDesc(
                    postId, fetchPageable);
        } else {
            // Các lần load tiếp theo
            Comment lastComment = commentRepository.findById(lastCommentId)
                    .orElseThrow(() -> new DataNotFoundException("Khong tim thay comment cuoi cung"));
            comments = commentRepository.findByPostIdAndParentCommentIsNullAndCreatedAtLessThanOrderByCreatedAtDesc(
                    postId, lastComment.getCreatedAt(), fetchPageable);
        }

        boolean hasNext = comments.size() > limit;
        if (hasNext) {
            comments = comments.subList(0, limit);
        }

        return new SliceImpl<>(comments, PageRequest.of(0, limit), hasNext);
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
        
        String recipientId = null;
        String recipientName = null;
        NotificationType type = null;
        String message = null;
        String targetType = null;
        String targetId = null;

        if (parentId != null) {
            Comment parentComment = commentRepository.findById(parentId)
                    .orElseThrow(() -> new DataNotFoundException("Không tìm thấy bình luận mà bạn muốn phản hồi"));
            commentRepository.incrementReplyCount(parentId);
            
            comment.setParentComment(parentComment);
            
            recipientId = parentComment.getUser().getId();
            recipientName = parentComment.getUser().getUsername();
            type = NotificationType.REPLY_COMMENT;
            message = user.getFullName() + " đã trả lời bình luận của bạn!";
            targetType = "COMMENT";
            targetId = parentComment.getId();
        } else {
            recipientId = post.getUser().getId();
            recipientName = post.getUser().getUsername();
            type = NotificationType.COMMENT_POST;
            message = user.getFullName() + " đã bình luận về bài viết của bạn!";
            targetType = "POST";
            targetId = post.getId();
        }
        postRepository.incrementCommentCount(postId);
        commentRepository.save(comment);

        if (recipientId != null && !user.getId().equals(recipientId)) {
            NotificationEvent event = NotificationEvent.builder()
                    .recipientId(recipientId)
                    .recipientName(recipientName)
                    .actorId(user.getId())
                    .type(type)
                    .targetType(targetType)
                    .targetId(targetId)
                    .message(message)
                    .build();
            eventPublisher.publishEvent(event);
        }
    }

    @Transactional("transactionManager")
    public void likeComment(User user, String commentId) throws Exception {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy bình luận"));
        CommentReaction commentReaction = commentReactionRepository.findByCommentAndUser(comment, user);

        boolean isNewLike = false;

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
            isNewLike = true;
        } else if (commentReaction.getType() == ReactionType.DISLIKE) {
            comment.setDisliked(comment.getDisliked() - 1);
            comment.setLiked(comment.getLiked() + 1);
            commentRepository.save(comment);
            commentReaction.setType(ReactionType.LIKE);
            commentReactionRepository.save(commentReaction);
            isNewLike = true;
        } else {
            comment.setLiked(comment.getLiked() - 1);
            commentRepository.save(comment);
            commentReactionRepository.delete(commentReaction);
        }

        if (isNewLike && !user.getId().equals(comment.getUser().getId())) {
            NotificationEvent event = NotificationEvent.builder()
                    .recipientId(comment.getUser().getId())
                    .recipientName(comment.getUser().getUsername())
                    .actorId(user.getId())
                    .type(NotificationType.LIKE_COMMENT)
                    .targetType("COMMENT")
                    .targetId(comment.getId())
                    .message(user.getFullName() + " đã thích bình luận của bạn!")
                    .build();
            eventPublisher.publishEvent(event);
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

    public Page<AdminCommentResponse> getCommentsByUserId(String userId, Pageable pageable) throws Exception {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DataNotFoundException("Người dùng không tồn tại"));

        return commentRepository.findByUser(user, pageable)
                .map(AdminCommentResponse::fromEntity);
    }
}
