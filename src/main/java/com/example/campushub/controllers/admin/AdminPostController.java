package com.example.campushub.controllers.admin;

import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.campushub.dtos.admin.AdminReportDTO;
import com.example.campushub.models.jpa.Comment;
import com.example.campushub.models.jpa.User;
import com.example.campushub.responses.ApiResponse;
import com.example.campushub.responses.PagedResponse;
import com.example.campushub.responses.admin.AdminCommentResponse;
import com.example.campushub.services.CommentService;
import com.example.campushub.services.PostService;
import com.example.campushub.services.ReportService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/posts")
@RequiredArgsConstructor
public class AdminPostController {
    private final PostService postService;
    private final CommentService commentService;
    private final ReportService reportService;

    @GetMapping("")
    public ResponseEntity<?> getPosts(@AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) throws Exception {
        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(PagedResponse.from(postService.searchPosts(query, status, pageable)));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<?> getPostDetail(@AuthenticationPrincipal User currentUser, @PathVariable String postId)
            throws Exception {
        return ResponseEntity.ok(ApiResponse.ok(postService.getAdminPostResponseById(postId)));
    }

    @GetMapping("/{postId}/comments")
    public ResponseEntity<?> getPostComments(@AuthenticationPrincipal User currentUser, @PathVariable String postId,
            @RequestParam(required = false) String lastCommentId,
            @RequestParam(defaultValue = "10") int limit) throws Exception {
        Slice<Comment> comments = commentService.getCommentsByPostId(postId, lastCommentId, limit);
        Map<String, Integer> replyCounts = commentService.getReplyCountsMap(comments.getContent());
        Slice<AdminCommentResponse> response = comments.map(comment -> AdminCommentResponse.fromEntity(
                comment,
                replyCounts.getOrDefault(comment.getId(), 0)));
        return ResponseEntity.ok(PagedResponse.from(response));
    }

    @PostMapping("/{postId}/report")
    public ResponseEntity<?> reportPost(@AuthenticationPrincipal User currentUser,
            @PathVariable String postId,
            @RequestBody @Valid AdminReportDTO dto) throws Exception {
        reportService.reportPostByAdmin(currentUser, postId, dto);
        return ResponseEntity.ok(ApiResponse.ok("Gỡ bài viết thành công"));
    }

    @PutMapping("/{postId}/active")
    public ResponseEntity<?> activePost(@AuthenticationPrincipal User currentUser, @PathVariable String postId)
            throws Exception {
        postService.adminActivePost(postId);
        return ResponseEntity.ok(ApiResponse.ok("Đã kích hoạt bài viết!"));
    }
}
