package com.example.campushub.controllers.users;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

import com.example.campushub.dtos.users.CreatePostDTO;
import com.example.campushub.models.jpa.Post;
import com.example.campushub.models.jpa.User;
import com.example.campushub.responses.ApiResponse;
import com.example.campushub.responses.PagedResponse;
import com.example.campushub.responses.PostResponse;
import com.example.campushub.services.PostService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users/posts")
@RequiredArgsConstructor
public class UserPostController {
    private final PostService postService;
    
    @PostMapping("")
    public ResponseEntity<?> createPost(@AuthenticationPrincipal User user, @RequestBody @Valid CreatePostDTO dto) throws Exception {
        postService.createPost(user, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Create post successfully"));
    }

    @PostMapping("/{postId}/like")
    public ResponseEntity<?> likePost(@AuthenticationPrincipal User user, @PathVariable String postId) throws Exception {
        postService.likePost(user, postId);
        return ResponseEntity.ok().body(ApiResponse.ok("Liked post successfully"));
    }

    @PostMapping("/{postId}/dislike")
    public ResponseEntity<?> dislikePost(@AuthenticationPrincipal User user, @PathVariable String postId) throws Exception {
        postService.dislikePost(user, postId);
        return ResponseEntity.ok().body(ApiResponse.ok("Disliked post successfully"));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<?> getPostById(@AuthenticationPrincipal User user, @PathVariable String postId) throws Exception {
        Post post = postService.getPostById(postId);
        String userReaction = postService.getUserReaction(post, user);
        return ResponseEntity.ok().body(PostResponse.builder()
                .id(post.getId())
                .content(post.getContent())
                .author(PostResponse.AuthorResponse.builder()
                        .id(post.getUser().getId())
                        .authorName(post.getUser().getFullName())
                        .department(post.getUser().getDepartment())
                        .build())
                .likeCount(post.getLiked())
                .dislikeCount(post.getDisliked())
                .userReaction(userReaction)
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build());
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMyPosts(@AuthenticationPrincipal User user) throws Exception {
        return ResponseEntity.ok().body(ApiResponse.ok(postService.getMyPostsResponses(user)));
    }

    @GetMapping("")
    public ResponseEntity<?> getPosts(@AuthenticationPrincipal User user, @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "3") int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "desc") String sortDir
    ) throws Exception {
        Sort sort = sortDir.equalsIgnoreCase("desc")
            ? Sort.by(sortBy).descending()
            : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok().body(ApiResponse.ok(PagedResponse.from(postService.getPostsForHomeResponses(pageable, user))));
    }
}
