package com.example.campushub.controllers;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.campushub.models.jpa.User;
import com.example.campushub.responses.ApiResponse;
import com.example.campushub.services.PostSeeder;
import com.example.campushub.services.UserSeeder;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/seed")
@Profile("dev")
@RequiredArgsConstructor
public class SeedController {

    private final UserSeeder userSeeder;
    private final PostSeeder postSeeder;

    @PostMapping("/users")
    public ResponseEntity<?> seedUsers(@RequestParam(defaultValue = "1") int count) {
        return ResponseEntity.ok(ApiResponse.ok("Đã tạo " + userSeeder.seedUser(count) + " người dùng giả thành công"));
    }

    @PostMapping("/posts")
    public ResponseEntity<?> seedPosts(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "1") int count,
            @RequestParam(defaultValue = "0") int maxReactions,
            @RequestParam(defaultValue = "false") boolean includeImages,
            @RequestParam(defaultValue = "false") boolean includeGroups) {
        int seededCount = postSeeder.seedPosts(user, count, maxReactions, includeImages, includeGroups);
        return ResponseEntity.ok().body(ApiResponse.ok("Seeded " + seededCount + " sample posts successfully"));
    }
}
