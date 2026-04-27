package com.example.campushub.controllers.users;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

import com.example.campushub.dtos.users.CreatePostDTO;
import com.example.campushub.dtos.users.CreateReportPostDTO;
import com.example.campushub.dtos.users.CreateSharePostDTO;
import com.example.campushub.dtos.users.UpdatePostDTO;
import com.example.campushub.models.jpa.Post;
import com.example.campushub.models.jpa.User;
import com.example.campushub.responses.ApiResponse;
import com.example.campushub.responses.PagedResponse;
import com.example.campushub.responses.PostResponse;
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
    public ResponseEntity<?> getMyPosts(@AuthenticationPrincipal User user, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) throws Exception {
        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        return ResponseEntity.ok()
                .body(ApiResponse.ok(PagedResponse.from(postService.getActivePostsByUserId(user.getId(), pageable, user))));
    }

    @GetMapping("/by-user/{userId}")
    public ResponseEntity<?> getPostsByUserId(@AuthenticationPrincipal User user, @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) throws Exception {
        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        return ResponseEntity.ok()
                .body(ApiResponse.ok(PagedResponse.from(postService.getActivePostsByUserId(userId, pageable, user))));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<?> getPostById(@AuthenticationPrincipal User user, @PathVariable String postId)
            throws Exception {
        Post post = postService.getActivePostById(postId);
        String userReaction = postService.getUserReaction(post, user);
        List<String> tags = postService.getTagsForPost(postId);
        List<String> sharedTags = post.getSharedPost() != null 
                ? postService.getTagsForPost(post.getSharedPost().getId()) 
                : null;
        return ResponseEntity.ok().body(ApiResponse.ok(PostResponse.fromPost(post, userReaction, tags, sharedTags)));
    }

    @GetMapping("")
    public ResponseEntity<?> getPosts(@AuthenticationPrincipal User user, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) throws Exception {
        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok()
                .body(ApiResponse.ok(PagedResponse.from(postService.getPostsForHomeResponses(pageable, user))));
    }

    @GetMapping("/topic/{topicName}")
    public ResponseEntity<?> getPostByTopicName(@AuthenticationPrincipal User user, @PathVariable String topicName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) throws Exception {
        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok()
                .body(ApiResponse.ok(PagedResponse.from(postService.getPostsByTopicName(user, topicName, pageable))));
    }

    @PostMapping("")
    public ResponseEntity<?> createPost(
            @AuthenticationPrincipal User user, 
            @ModelAttribute @Valid CreatePostDTO dto,
            @RequestParam(value = "images", required = false) List<MultipartFile> images)
            throws Exception {
        postService.createPost(user, dto, images);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Create post successfully"));
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

    @PutMapping("/{postId}")
    public ResponseEntity<?> editPost(@AuthenticationPrincipal User user, @PathVariable String postId,
            @RequestBody @Valid UpdatePostDTO dto) throws Exception {
        postService.editPost(user, postId, dto);
        return ResponseEntity.ok().body(ApiResponse.ok(ApiResponse.ok("Cập nhật bài viết thành công")));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<?> deletePost(@AuthenticationPrincipal User user, @PathVariable String postId)
            throws Exception {
        postService.deletePost(user, postId);
        return ResponseEntity.ok().body(ApiResponse.ok("Xóa bài viết thành công"));
    }
}
