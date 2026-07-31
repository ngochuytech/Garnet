package com.example.campushub.controllers.users;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.campushub.models.jpa.User;
import com.example.campushub.responses.ApiResponse;
import com.example.campushub.services.TopicService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users/topics")
@RequiredArgsConstructor
public class UserTopicController {
    
    private final TopicService topicService;

    @GetMapping("")
    public ResponseEntity<?> getTopicCounts(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok().body(ApiResponse.ok(topicService.getTopicCounts(user)));
    }

    @GetMapping("/{topicName}")
    public ResponseEntity<?> getTopicDetails(@AuthenticationPrincipal User user, @PathVariable String topicName) throws Exception{
        return ResponseEntity.ok().body(ApiResponse.ok(topicService.getTopicDetails(topicName)));
    }

    @PutMapping("/image")
    public ResponseEntity<?> updateTopicImage(
            @AuthenticationPrincipal User user,
            @RequestParam String topicName,
            @RequestParam String imageUrl) throws Exception {
        topicService.updateTopicImage(user, topicName, imageUrl);
        return ResponseEntity.ok().body(ApiResponse.ok("Cập nhật ảnh chủ đề thành công"));
    }
}
