package com.example.campushub.controllers.users;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.campushub.responses.ApiResponse;
import com.example.campushub.services.MediaStorageService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users/media")
@RequiredArgsConstructor
public class UserMediaController {
    private final MediaStorageService mediaStorageService;

    @GetMapping("/generate-video-url")
    public ResponseEntity<?> getPresignedUrl(@RequestParam String fileName) {
        try {
            String presignedUrl = mediaStorageService.generatePresignedUrl(fileName, "videos");
            Map<String, String> response = new HashMap<>();
            response.put("uploadUrl", presignedUrl);
            return ResponseEntity.ok(ApiResponse.ok(response));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("Không thể tạo link upload AWS S3 cho video"));
        }
    }

    @GetMapping("/generate-image-url")
    public ResponseEntity<?> getImagePresignedUrl(@RequestParam String fileName, @RequestParam(defaultValue = "images") String category) {
        try {
            String presignedUrl = mediaStorageService.generatePresignedUrl(fileName, category);
            Map<String, String> response = new HashMap<>();
            response.put("uploadUrl", presignedUrl);
            return ResponseEntity.ok(ApiResponse.ok(response));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("Không thể tạo link upload AWS S3 cho ảnh"));
        }
    }
}
