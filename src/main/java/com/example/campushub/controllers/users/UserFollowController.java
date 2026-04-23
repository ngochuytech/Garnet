package com.example.campushub.controllers.users;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.campushub.models.jpa.User;
import com.example.campushub.responses.ApiResponse;
import com.example.campushub.responses.FollowResponse;
import com.example.campushub.responses.PagedResponse;
import com.example.campushub.services.FollowService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserFollowController {
    private final FollowService followService;

    @PostMapping("/{targetId}/follow")
    public ResponseEntity<?> followUser(@PathVariable String targetId,
            @AuthenticationPrincipal User currentUser) throws Exception {
        followService.followUser(currentUser.getId(), targetId);
        return ResponseEntity.ok(ApiResponse.ok("Đã theo dõi thành công!"));
    }

    @PostMapping("/{targetId}/unfollow")
    public ResponseEntity<?> unfollowUser(@PathVariable String targetId,
            @AuthenticationPrincipal User currentUser) throws Exception {
        followService.unfollowUser(currentUser.getId(), targetId);
        return ResponseEntity.ok(ApiResponse.ok("Đã bỏ theo dõi thành công!"));
    }

    @GetMapping("/suggestions")
    public ResponseEntity<?> getWhoToFollow(@AuthenticationPrincipal User currentUser) {
        List<FollowResponse> suggestedUsers = followService.getWhoToFollow(currentUser.getId())
                .stream()
                .map(suggestedUser -> FollowResponse.builder()
                        .id(suggestedUser.getId())
                        .fullName(suggestedUser.getFullName())
                        .avatarUrl(suggestedUser.getAvatarUrl())
                        .department(suggestedUser.getDepartment())
                        .build())
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(suggestedUsers));
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchUsers(@AuthenticationPrincipal User currentUser, @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) throws Exception {
        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<FollowResponse> searchResults = followService.searchUsers(currentUser.getId(), query, pageable);
        return ResponseEntity.ok(PagedResponse.from(searchResults));
    }
}
