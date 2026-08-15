package com.example.campushub.controllers.users;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.campushub.dtos.users.CreatePostDTO;
import com.example.campushub.dtos.users.CreateReportPostDTO;
import com.example.campushub.dtos.users.CreateSharePostDTO;
import com.example.campushub.models.jpa.User;
import com.example.campushub.responses.ApiResponse;
import com.example.campushub.services.PostService;
import com.example.campushub.services.ReportService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users/posts")
@RequiredArgsConstructor
public class UserPostController {
    private final PostService postService;
    private final ReportService reportService;

    @GetMapping("/me")
    public ResponseEntity<?> getMyPosts(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor) throws Exception {
        return ResponseEntity.ok()
                .body(ApiResponse.ok(postService.getActivePostsByUserId(user.getId(), size, cursor, user)));
    }

    @GetMapping("/by-user/{userId}")
    public ResponseEntity<?> getPostsByUserId(
            @AuthenticationPrincipal User user,
            @PathVariable String userId,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor) throws Exception {
        return ResponseEntity.ok()
                .body(ApiResponse.ok(postService.getActivePostsByUserId(userId, size, cursor, user)));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<?> getPostById(@AuthenticationPrincipal User user, @PathVariable String postId)
            throws Exception {
        return ResponseEntity.ok().body(ApiResponse.ok(postService.getActivePostResponseById(postId, user)));
    }

    @GetMapping("")
    public ResponseEntity<?> getPosts(@AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor) throws Exception {
        return ResponseEntity.ok()
                .body(ApiResponse.ok(postService.getPostsForHomeResponses(size, cursor, user)));
    }

    @GetMapping("/topic/{topicName}")
    public ResponseEntity<?> getPostByTopicName(@AuthenticationPrincipal User user, @PathVariable String topicName,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor) throws Exception {
        return ResponseEntity.ok()
                .body(ApiResponse.ok(postService.getPostsByTopicName(user, topicName, size, cursor)));
    }

    @PostMapping("")
    public ResponseEntity<?> createPost(
            @AuthenticationPrincipal User user,
            @ModelAttribute @Valid CreatePostDTO dto)
            throws Exception {
        postService.createPost(user, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Tạo bài viết thành công"));
    }


    @PostMapping("/{postId}/like")
    public ResponseEntity<?> likePost(@AuthenticationPrincipal User user, @PathVariable String postId)
            throws Exception {
        postService.likePost(user, postId);
        return ResponseEntity.ok().body(ApiResponse.ok("Liked post successfully"));
    }

    @PostMapping("/{postId}/dislike")
    public ResponseEntity<?> dislikePost(@AuthenticationPrincipal User user, @PathVariable String postId)
            throws Exception {
        postService.dislikePost(user, postId);
        return ResponseEntity.ok().body(ApiResponse.ok("Disliked post successfully"));
    }

    @PostMapping("/{postId}/report")
    public ResponseEntity<?> reportPost(@AuthenticationPrincipal User user, @PathVariable String postId,
            @RequestBody @Valid CreateReportPostDTO dto) throws Exception {
        reportService.createReportPost(user, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Báo cáo bài viết thành công!"));
    }

    @PostMapping("/{postId}/share")
    public ResponseEntity<?> sharePost(@AuthenticationPrincipal User user, @PathVariable String postId,
            @RequestBody CreateSharePostDTO dto) throws Exception {
        postService.sharePost(user, postId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Chia sẻ bài viết thành công"));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<?> deletePost(@AuthenticationPrincipal User user, @PathVariable String postId)
            throws Exception {
        postService.deletePost(user, postId);
        return ResponseEntity.ok().body(ApiResponse.ok("Xóa bài viết thành công"));
    }
}
