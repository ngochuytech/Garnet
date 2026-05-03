package com.example.campushub.controllers.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.campushub.models.jpa.User;
import com.example.campushub.responses.ApiResponse;
import com.example.campushub.services.AdminDashboardService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {
	private final AdminDashboardService adminDashboardService;

	@GetMapping("/stat")
	public ResponseEntity<?> getDashboardSummary(@AuthenticationPrincipal User currentUser) {
		return ResponseEntity.ok(ApiResponse.ok(adminDashboardService.getSummary()));
	}

	@GetMapping("/user-growth")
	public ResponseEntity<?> getUserGrowth(@AuthenticationPrincipal User currentUser) {
		return ResponseEntity.ok(ApiResponse.ok(adminDashboardService.getUserGrowth()));
	}

	@GetMapping("/topic-distribution")
	public ResponseEntity<?> getTopicDistribution(@AuthenticationPrincipal User currentUser) {
		return ResponseEntity.ok(ApiResponse.ok(adminDashboardService.getTopicDistribution()));
	}
}
