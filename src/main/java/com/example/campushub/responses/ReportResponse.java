package com.example.campushub.responses;

import java.time.LocalDateTime;
import com.example.campushub.enums.ReportStatus;
import com.example.campushub.enums.ReportType;
import com.example.campushub.models.jpa.Report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReportResponse {
    private String id;
    private ReporterResponse reporter;
    private ReportedResponse reportedUser;
    private ReportType targetType;
    private String targetId;
    private String reason;
    private String description;
    private ReportTargetResponse target;
    private ReportStatus status;
    private String adminNotes;
    private HandledByResponse handledBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ReporterResponse {
        private String id;
        private String fullName;
        private String avatarUrl;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ReportedResponse {
        private String id;
        private String fullName;
        private String avatarUrl;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class HandledByResponse {
        private String id;
        private String fullName;
        private String avatarUrl;
    }

    public static ReportResponse fromEntity(Report report) {
        return fromEntity(report, null);
    }

    public static ReportResponse fromEntity(Report report, ReportTargetResponse target) {
        return ReportResponse.builder()
                .id(report.getId())
                .reporter(ReporterResponse.builder()
                        .id(report.getReporter().getId())
                        .fullName(report.getReporter().getFullName())
                        .avatarUrl(report.getReporter().getAvatarUrl())
                        .build())
                .reportedUser(ReportedResponse.builder()
                        .id(report.getReportedUser().getId())
                        .fullName(report.getReportedUser().getFullName())
                        .avatarUrl(report.getReportedUser().getAvatarUrl())
                        .build())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .reason(report.getReason())
                .description(report.getDescription())
                .target(target)
                .status(report.getStatus())
                .adminNotes(report.getAdminNote())
                .handledBy(HandledByResponse.builder()
                    .id(report.getResolvedBy() != null ? report.getResolvedBy().getId() : null)
                    .fullName(report.getResolvedBy() != null ? report.getResolvedBy().getFullName() : null)
                    .avatarUrl(report.getResolvedBy() != null ? report.getResolvedBy().getAvatarUrl() : null)
                    .build()
                )
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .build();
    }
}
