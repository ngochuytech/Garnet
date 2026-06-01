package com.example.campushub.controllers.users;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.campushub.dtos.users.CreateCommentDTO;
import com.example.campushub.dtos.users.CreateReportCommentDTO;
import com.example.campushub.models.jpa.Comment;
import com.example.campushub.models.jpa.User;
import com.example.campushub.responses.ApiResponse;
import com.example.campushub.responses.CommentResponse;
import com.example.campushub.responses.PagedResponse;
import com.example.campushub.services.CommentService;
import com.example.campushub.services.ReportService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users/comments")
@RequiredArgsConstructor
public class UserCommentController {
    private final CommentService commentService;
    private final ReportService reportService;

    @GetMapping("")
    public ResponseEntity<?> getCommentsByPostId(@AuthenticationPrincipal User user, @RequestParam String postId,
            @RequestParam(required = false) String lastCommentId,
            @RequestParam(defaultValue = "10") int limit) throws Exception {
        Slice<Comment> comments = commentService.getCommentsByPostId(postId, lastCommentId, limit);

        Map<String, String> userReactionsMap = commentService.getUserReactionsMap(user, postId);

        Slice<CommentResponse> responseSlice = comments
            .map(comment -> CommentResponse.fromComment(comment, userReactionsMap));
        return ResponseEntity.ok().body(ApiResponse.ok(PagedResponse.from(responseSlice)));
    }

    @GetMapping("/{commentId}/replies")
    public ResponseEntity<?> getRepliesByCommentId(@AuthenticationPrincipal User user, @PathVariable String commentId,
            @RequestParam(required = false) String lastCommentId,
            @RequestParam(defaultValue = "10") Integer limit) throws Exception {
        List<Comment> comments = commentService.getCommentReplies(commentId, lastCommentId, limit);

        Map<String, String> userReactionsMap = commentService.getUserReactionsMapForComments(user, comments);

        List<CommentResponse> commentResponses = comments.stream()
                .map(comment -> CommentResponse.fromComment(comment, userReactionsMap))
                .toList();
        return ResponseEntity.ok().body(ApiResponse.ok(commentResponses));
    }

    @PostMapping("/post/{postId}")
    public ResponseEntity<?> createCommentFromPost(@AuthenticationPrincipal User user,
            @PathVariable String postId,
            @RequestBody @Valid CreateCommentDTO dto) throws Exception {
        commentService.createComment(user, postId, dto.getParentId(), dto.getContent());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Đã bình luận thành công!"));
    }

    @PostMapping("/{commentId}/like")
    public ResponseEntity<?> likeComment(@AuthenticationPrincipal User user, @PathVariable String commentId)
            throws Exception {
        commentService.likeComment(user, commentId);
        return ResponseEntity.ok().body(ApiResponse.ok("Liked comment successfully"));
    }

    @PostMapping("/{commentId}/dislike")
    public ResponseEntity<?> dislikeComment(@AuthenticationPrincipal User user, @PathVariable String commentId)
            throws Exception {
        commentService.dislikeComment(user, commentId);
        return ResponseEntity.ok().body(ApiResponse.ok("Disliked comment successfully"));
    }

    @PostMapping("/{commentId}/report")
    public ResponseEntity<?> reportComment(@AuthenticationPrincipal User user, @PathVariable String commentId,
            @RequestBody @Valid CreateReportCommentDTO dto) throws Exception {
        reportService.reportComment(user, commentId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Báo cáo bình luận thành công!"));
    }
}
