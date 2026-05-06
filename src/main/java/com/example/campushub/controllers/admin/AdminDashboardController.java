package com.example.campushub.controllers.admin;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.campushub.models.jpa.User;
import com.example.campushub.responses.ApiResponse;
import com.example.campushub.services.AdminDashboardService;
import com.example.campushub.services.ReportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {
	private final AdminDashboardService adminDashboardService;
	private final ReportService reportService;

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

	@GetMapping("/weekly-reports")
	public ResponseEntity<?> getWeeklyReport(@AuthenticationPrincipal User currentUser,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size,
			@RequestParam(defaultValue = "createdAt") String sortBy,
			@RequestParam(defaultValue = "desc") String sortDir) throws Exception {
		Sort sort = sortDir.equalsIgnoreCase("desc")
				? Sort.by(sortBy).descending()
				: Sort.by(sortBy).ascending();
		Pageable pageable = PageRequest.of(page, size, sort);
		return ResponseEntity.ok(ApiResponse.ok(reportService.getWeeklyReports(pageable)));
	}
}
