package com.example.campushub.controllers.system;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.campushub.dtos.activity.ImpressionRequest;
import com.example.campushub.models.jpa.User;
import com.example.campushub.responses.ApiResponse;
import com.example.campushub.services.ActivityService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/activity")
@RequiredArgsConstructor
public class ActivityController {
    private final ActivityService activityService;

    @PostMapping("/impressions")
    public ResponseEntity<?> logImpressions(
            @AuthenticationPrincipal User currentUser,
            @RequestBody @Valid ImpressionRequest request) {
        int recorded = activityService.recordPostImpressions(currentUser.getId(), request.getEvents());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("recorded", recorded)));
    }
}
