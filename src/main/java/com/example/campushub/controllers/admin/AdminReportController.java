package com.example.campushub.controllers.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.campushub.dtos.admin.AdminReportDTO;
import com.example.campushub.models.jpa.Report;
import com.example.campushub.models.jpa.User;
import com.example.campushub.responses.ApiResponse;
import com.example.campushub.responses.PagedResponse;
import com.example.campushub.responses.ReportResponse;
import com.example.campushub.services.ReportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {
    private final ReportService reportService;

    @GetMapping("/status/{status}/type/{type}")
    public ResponseEntity<?> getReportByStatus(@AuthenticationPrincipal User currentUser,
            @PathVariable String status,
            @PathVariable String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) throws Exception {
        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<ReportResponse> reports;
        if (status.equals("ALL"))
            reports = reportService.getReports(type, pageable);
        else
            reports = reportService.getReports(status, type, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PagedResponse.from(reports)));
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<?> getReportDetail(@AuthenticationPrincipal User currentUser,
            @PathVariable String reportId) throws Exception {
        Report report = reportService.getReportDetail(reportId);
        return ResponseEntity.ok(ApiResponse.ok(ReportResponse.fromEntity(report)));
    }

    @GetMapping("/search")
    public ResponseEntity<?> getReportBySearch(@AuthenticationPrincipal User currentUser,
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) throws Exception {
        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<ReportResponse> response = reportService.searchReports(query, pageable);
        return ResponseEntity.ok(PagedResponse.from(response));
    }

    @PutMapping("/{reportId}/close")
    public ResponseEntity<?> closeReport(@AuthenticationPrincipal User currentUser,
            @PathVariable String reportId) throws Exception {
        reportService.closeReport(currentUser, reportId);
        return ResponseEntity.ok(ApiResponse.ok("Đã đóng báo cáo!"));
    }

    @PutMapping("/{reportId}/resolve")
    public ResponseEntity<?> resolveReport(@AuthenticationPrincipal User currentUser,
            @PathVariable String reportId,
            @RequestBody AdminReportDTO adminReportDTO) throws Exception {
        reportService.handleReportResolution(currentUser, reportId, adminReportDTO.getAdminNotes());
        return ResponseEntity.ok(ApiResponse.ok("Đã giải quyết báo cáo!"));
    }
}
