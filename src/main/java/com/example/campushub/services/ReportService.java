package com.example.campushub.services;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import com.example.campushub.responses.PagedResponse;
import com.example.campushub.responses.admin.AdminReportResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.campushub.dtos.admin.AdminReportDTO;
import com.example.campushub.dtos.users.CreateReportPostDTO;
import com.example.campushub.enums.ContentStatus;
import com.example.campushub.enums.ReportStatus;
import com.example.campushub.enums.ReportType;
import com.example.campushub.exceptions.DataNotFoundException;
import com.example.campushub.exceptions.InvalidParamException;
import com.example.campushub.models.jpa.Post;
import com.example.campushub.models.jpa.Report;
import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.PostRepository;
import com.example.campushub.repositories.jpa.ReportRepository;
import com.example.campushub.repositories.jpa.UserRepository;
import com.example.campushub.repositories.neo4j.PostNeo4jRepository;
import com.example.campushub.responses.ReportResponse;
import com.example.campushub.responses.admin.AdminReportResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReportService {
    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostNeo4jRepository postNeo4jRepository;

    private ReportType parseAndValidateTargetType(String targetType) {
        try {
            return ReportType.valueOf(targetType.toUpperCase());
        } catch (InvalidParamException e) {
            throw new InvalidParamException("Tham số target type không hợp lệ" + targetType);
        }
    }

    private ReportStatus parseAndValidateReportStatus(String status) {
        try {
            return ReportStatus.valueOf(status.toUpperCase());
        } catch (InvalidParamException e) {
            throw new InvalidParamException("Tham số report status không hợp lệ" + status);
        }
    }

    private ReportType parseAndValidateReportType(String type) {
        if (type == null || type.isBlank() || type.equalsIgnoreCase("ALL")) {
            return null;
        }
        try {
            return ReportType.valueOf(type.toUpperCase());
        } catch (InvalidParamException e) {
            throw new InvalidParamException("Tham số report type không hợp lệ" + type);
        }
    }

    public void createReportPost(User reporter, CreateReportPostDTO dto) throws Exception {
        ReportType type = parseAndValidateTargetType(dto.getTargetType());
        if (type != ReportType.POST)
            throw new InvalidParamException("Báo cáo không hợp lệ!");
        Post post = postRepository.findById(dto.getTargetId())
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy bài viết cần báo cáo!"));

        if (reportRepository.existsByReporterAndTargetTypeAndTargetId(reporter, type, post.getId())) {
            throw new InvalidParamException("Bạn đã báo cáo bài viết này rồi!");
        }

        Report report = Report.builder()
                .reporter(reporter)
                .targetType(type)
                .targetId(post.getId())
                .reportedUser(post.getUser())
                .reason(dto.getReason())
                .description(dto.getDescription())
                .reportedContentSnapshot(post.getContent())
                .status(ReportStatus.OPEN)
                .build();

        reportRepository.save(report);
    }

    public Report getReportDetail(String reportId) throws Exception {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy báo cáo!"));
        return report;
    }

    public Page<ReportResponse> getReports(String status, String type, Pageable pageable) throws Exception {
        ReportStatus reportStatus = parseAndValidateReportStatus(status);
        ReportType reportType = parseAndValidateReportType(type);
        return reportRepository.findByStatusAndOptionalType(reportStatus, reportType, pageable)
                .map(ReportResponse::fromEntity);
    }

    public Page<ReportResponse> getReports(String type, Pageable pageable) throws Exception {
        ReportType reportType = parseAndValidateReportType(type);
        return reportRepository.findByOptionalType(reportType, pageable)
                .map(ReportResponse::fromEntity);
    }

    @Transactional(value = "transactionManager")
    public void closeReport(User currentUser, String reportId) throws Exception {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy báo cáo!"));
        report.setStatus(ReportStatus.CLOSED);
        report.setResolvedBy(currentUser);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void handleReportResolution(User currentUser, String reportId, String adminNote) throws Exception {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy báo cáo!"));

        String targetPostId = report.getTargetId();
        reportRepository.updateExistingReportsStatus(targetPostId, ReportType.POST, ReportStatus.RESOLVED, currentUser, adminNote);

        Post post = postRepository.findById(report.getTargetId())
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy bài viết liên quan!"));
        post.setStatus(ContentStatus.REPORTED);
        try {
            postNeo4jRepository.updatePostStatus(post.getId(), ContentStatus.REPORTED.name());
        } catch (Exception e) {
            throw new Exception("Báo cáo đã được giải quyết nhưng có lỗi khi cập nhật trạng thái bài viết trên Neo4j: "
                    + e.getMessage());
        }
    }

    public Page<ReportResponse> searchReports(String query, Pageable pageable) throws Exception {
        return reportRepository.searchReports(query, pageable).map(ReportResponse::fromEntity);
    }

    public Page<AdminReportResponse> getReportsByUserId(String userId, Pageable pageable) throws Exception {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DataNotFoundException("Người dùng không tồn tại"));

        return reportRepository.findByReportedUser(user, pageable)
                .map(AdminReportResponse::fromEntity);
    }

    public PagedResponse<AdminReportResponse> getWeeklyReports(Pageable pageable) throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDateTime start = monday.atStartOfDay();
        LocalDateTime end = LocalDateTime.now();

        Page<Report> page = reportRepository.findByCreatedAtBetween(start, end, pageable);
        Page<AdminReportResponse> mapped = page.map(AdminReportResponse::fromEntity);
        return PagedResponse.from(mapped);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void reportPostByAdmin(User admin, String postId, AdminReportDTO dto) throws Exception {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy bài viết"));
        
        reportRepository.updateExistingReportsStatus(postId, ReportType.POST, ReportStatus.RESOLVED, admin, postId);
        
        Report adminReport = Report.builder()
                .targetId(postId)
                .targetType(ReportType.POST)
                .reporter(admin)
                .reportedUser(post.getUser())
                .reportedContentSnapshot(post.getContent())
                .resolvedBy(admin)
                .adminNote(dto.getAdminNotes())
                .reason(dto.getReason())
                .status(ReportStatus.RESOLVED)
                .build();
        reportRepository.save(adminReport);
        
        post.setStatus(ContentStatus.REPORTED);

        try {
            postNeo4jRepository.updatePostStatus(postId, ContentStatus.REPORTED.name());
        } catch (Exception e) {
            throw new Exception("Gỡ bài viết thành công nhưng có lỗi khi cập nhật trạng thái trên Neo4j: "
                    + e.getMessage());
        }
    }

}
