package com.example.campushub.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.example.campushub.repositories.neo4j.PostNeo4jRepository;
import com.example.campushub.responses.ReportResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReportService {
    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
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

    public void closeReport(User currentUser, String reportId) throws Exception {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy báo cáo!"));
        report.setStatus(ReportStatus.CLOSED);
        report.setResolvedBy(currentUser);
        reportRepository.save(report);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void handleReportResolution(User currentUser, String reportId, String adminNote) throws Exception {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy báo cáo!"));
        report.setStatus(ReportStatus.RESOLVED);
        report.setAdminNote(adminNote);
        report.setResolvedBy(currentUser);
        reportRepository.save(report);

        Post post = postRepository.findById(report.getTargetId())
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy bài viết liên quan!"));
        post.setStatus(ContentStatus.REPORTED);
        postRepository.save(post);
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

}
