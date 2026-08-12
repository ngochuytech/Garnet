package com.example.campushub.services;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.campushub.dtos.admin.AdminGroupReportDTO;
import com.example.campushub.dtos.admin.AdminReportDTO;
import com.example.campushub.dtos.record.posts.PostCommentChangedPayload;
import com.example.campushub.dtos.record.posts.PostStatusChangedPayload;
import com.example.campushub.dtos.users.CreateReportCommentDTO;
import com.example.campushub.dtos.users.CreateReportGroupDTO;
import com.example.campushub.dtos.users.CreateReportPostDTO;
import com.example.campushub.enums.ContentStatus;
import com.example.campushub.enums.GroupModerationAction;
import com.example.campushub.enums.GroupStatus;
import com.example.campushub.enums.MemberRole;
import com.example.campushub.enums.MemberStatus;
import com.example.campushub.enums.Neo4jEventType;
import com.example.campushub.enums.NotificationType;
import com.example.campushub.enums.ReportStatus;
import com.example.campushub.enums.ReportType;
import com.example.campushub.enums.UserRole;
import com.example.campushub.events.NotificationEvent;
import com.example.campushub.exceptions.ResourceNotFoundException;
import com.example.campushub.exceptions.BadRequestException;
import com.example.campushub.models.jpa.Comment;
import com.example.campushub.models.jpa.Group;
import com.example.campushub.models.jpa.GroupMember;
import com.example.campushub.models.jpa.Neo4jSyncEvent;
import com.example.campushub.models.jpa.Post;
import com.example.campushub.models.jpa.Report;
import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.CommentRepository;
import com.example.campushub.repositories.jpa.GroupMemberRepository;
import com.example.campushub.repositories.jpa.GroupRepository;
import com.example.campushub.repositories.jpa.Neo4jSyncEventRepository;
import com.example.campushub.repositories.jpa.PostRepository;
import com.example.campushub.repositories.jpa.ReportRepository;
import com.example.campushub.repositories.jpa.UserRepository;
import com.example.campushub.responses.ReportResponse;
import com.example.campushub.responses.ReportTargetResponse;
import com.example.campushub.responses.admin.AdminReportResponse;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ReportService {
    private final Neo4jSyncEventRepository neo4jSyncEventRepository;
    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    private String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert object to JSON", e);
        }
    }

    private ReportType parseAndValidateTargetType(String targetType) {
        try {
            return ReportType.valueOf(targetType.toUpperCase());
        } catch (BadRequestException e) {
            throw new BadRequestException("Tham số target type không hợp lệ" + targetType);
        }
    }

    private ReportStatus parseAndValidateReportStatus(String status) {
        try {
            return ReportStatus.valueOf(status.toUpperCase());
        } catch (BadRequestException e) {
            throw new BadRequestException("Tham số report status không hợp lệ" + status);
        }
    }

    private ReportType parseAndValidateReportType(String type) {
        if (type == null || type.isBlank() || type.equalsIgnoreCase("ALL")) {
            return null;
        }
        try {
            return ReportType.valueOf(type.toUpperCase());
        } catch (BadRequestException e) {
            throw new BadRequestException("Tham số report type không hợp lệ" + type);
        }
    }

    public void createReportPost(User reporter, CreateReportPostDTO dto) throws Exception {
        ReportType type = parseAndValidateTargetType(dto.getTargetType());
        if (type != ReportType.POST)
            throw new BadRequestException("Báo cáo không hợp lệ!");
        Post post = postRepository.findById(dto.getTargetId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài viết cần báo cáo!"));

        if (reportRepository.existsByReporterAndTargetTypeAndTargetIdAndStatus(reporter, type, post.getId(),
                ReportStatus.OPEN)) {
            throw new BadRequestException("Bạn đã báo cáo bài viết này rồi!");
        }

        Report report = Report.builder()
                .reporter(reporter)
                .targetType(type)
                .targetId(post.getId())
                .reportedUser(post.getUser())
                .reason(dto.getReason())
                .description(dto.getDescription())
                .status(ReportStatus.OPEN)
                .build();

        reportRepository.save(report);
    }

    public void createReportGroup(User reporter, String groupId, CreateReportGroupDTO dto) throws Exception {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm cần báo cáo!"));

        if (reportRepository.existsByReporterAndTargetTypeAndTargetIdAndStatus(reporter, ReportType.GROUP,
                group.getId(), ReportStatus.OPEN)) {
            throw new BadRequestException("Bạn đã báo cáo nhóm này và báo cáo đang chờ xử lý!");
        }

        GroupMember leader = groupMemberRepository
                .findFirstByGroup_IdAndRoleAndStatus(groupId, MemberRole.LEADER, MemberStatus.APPROVED)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trưởng nhóm"));

        Report report = Report.builder()
                .reporter(reporter)
                .targetType(ReportType.GROUP)
                .targetId(group.getId())
                .reportedUser(leader.getUser())
                .reason(dto.getReason())
                .description(dto.getDescription())
                .status(ReportStatus.OPEN)
                .build();

        reportRepository.save(report);
    }

    public void reportComment(User reporter, String commentId, CreateReportCommentDTO dto) throws Exception {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bình luận cần báo cáo!"));

        if (comment.getStatus() != ContentStatus.ACTIVE) {
            throw new ResourceNotFoundException("Bình luận này không tồn tại hoặc đã bị xóa!");
        }

        if (reportRepository.existsByReporterAndTargetTypeAndTargetIdAndStatus(
                reporter, ReportType.COMMENT, comment.getId(), ReportStatus.OPEN)) {
            throw new BadRequestException("Bạn đã báo cáo bình luận này và báo cáo đang chờ xử lý!");
        }

        Report report = Report.builder()
                .reporter(reporter)
                .targetType(ReportType.COMMENT)
                .targetId(comment.getId())
                .reportedUser(comment.getUser())
                .reason(dto.getReason())
                .description(dto.getDescription())
                .status(ReportStatus.OPEN)
                .build();

        reportRepository.save(report);
    }

    @Transactional(value = "transactionManager", readOnly = true)
    public Report getReportDetail(String reportId) throws Exception {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy báo cáo!"));
        return report;
    }

    @Transactional(value = "transactionManager", readOnly = true)
    public ReportResponse getReportDetailResponse(String reportId) throws Exception {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy báo cáo!"));
        return toReportResponse(report);
    }

    @Transactional(value = "transactionManager", readOnly = true)
    public Page<ReportResponse> getReports(String status, String type, Pageable pageable) throws Exception {
        ReportStatus reportStatus = parseAndValidateReportStatus(status);
        ReportType reportType = parseAndValidateReportType(type);
        return reportRepository.findByStatusAndOptionalType(reportStatus, reportType, pageable)
                .map(this::toReportResponse);
    }

    @Transactional(value = "transactionManager", readOnly = true)
    public Page<ReportResponse> getReports(String type, Pageable pageable) throws Exception {
        ReportType reportType = parseAndValidateReportType(type);
        return reportRepository.findByOptionalType(reportType, pageable)
                .map(this::toReportResponse);
    }

    @Transactional(value = "transactionManager")
    public void closeReport(User currentUser, String reportId) throws Exception {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy báo cáo!"));
        report.setStatus(ReportStatus.CLOSED);
        report.setResolvedBy(currentUser);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void handleReportResolution(User currentUser, String reportId, AdminReportDTO dto) throws Exception {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy báo cáo!"));

        String adminNote = dto.getAdminNotes();
        boolean isReporterAdmin = report.getReporter() != null && report.getReporter().getRole() == UserRole.ADMIN;

        if (report.getTargetType() == ReportType.GROUP) {
            Group group = groupRepository.findById(report.getTargetId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm liên quan!"));
            group.setStatus(GroupStatus.ARCHIVED);
            reportRepository.updateExistingReportsStatus(report.getTargetId(), ReportType.GROUP, ReportStatus.RESOLVED,
                    currentUser, adminNote);

            if (!isReporterAdmin) {
                Report adminReport = Report.builder()
                        .targetId(group.getId())
                        .targetType(ReportType.GROUP)
                        .reporter(currentUser)
                        .reportedUser(report.getReportedUser())
                        .resolvedBy(currentUser)
                        .adminNote(adminNote)
                        .reason(dto.getReason() != null ? dto.getReason() : report.getReason())
                        .description(report.getDescription())
                        .status(ReportStatus.RESOLVED)
                        .build();
                reportRepository.save(adminReport);
            }

            publishNotification(
                    currentUser,
                    report.getReportedUser(),
                    NotificationType.GROUP_LOCKED,
                    "GROUP",
                    group.getId(),
                    "Nhóm \"" + group.getName() + "\" đã bị khóa/ẩn do vi phạm.");
            return;
        }

        if (report.getTargetType() == ReportType.COMMENT) {
            String targetCommentId = report.getTargetId();
            reportRepository.updateExistingReportsStatus(targetCommentId, ReportType.COMMENT, ReportStatus.RESOLVED,
                    currentUser, adminNote);

            Comment comment = commentRepository.findById(targetCommentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bình luận liên quan!"));
            if (comment.getStatus() == ContentStatus.ACTIVE) {
                hideActiveCommentTree(comment);
            }

            if (!isReporterAdmin) {
                Report adminReport = Report.builder()
                        .targetId(comment.getId())
                        .targetType(ReportType.COMMENT)
                        .reporter(currentUser)
                        .reportedUser(comment.getUser())
                        .resolvedBy(currentUser)
                        .adminNote(adminNote)
                        .reason(dto.getReason() != null ? dto.getReason() : report.getReason())
                        .description(report.getDescription())
                        .status(ReportStatus.RESOLVED)
                        .build();
                reportRepository.save(adminReport);
            }
            return;
        }

        if (report.getTargetType() != ReportType.POST) {
            throw new BadRequestException("Loại báo cáo này chưa được hỗ trợ xử lý tự động");
        }

        String targetPostId = report.getTargetId();
        reportRepository.updateExistingReportsStatus(targetPostId, ReportType.POST, ReportStatus.RESOLVED, currentUser,
                adminNote);

        Post post = postRepository.findById(report.getTargetId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài viết liên quan!"));
        if (!isReporterAdmin) {
            Report adminReport = Report.builder()
                    .targetId(post.getId())
                    .targetType(ReportType.POST)
                    .reporter(currentUser)
                    .reportedUser(post.getUser())
                    .resolvedBy(currentUser)
                    .adminNote(adminNote)
                    .reason(dto.getReason() != null ? dto.getReason() : report.getReason())
                    .description(report.getDescription())
                    .status(ReportStatus.RESOLVED)
                    .build();
            reportRepository.save(adminReport);
        }

        markPostReportedAndSynchronize(post);
    }

    @Transactional(value = "transactionManager", readOnly = true)
    public Page<ReportResponse> searchReports(String query, Pageable pageable) throws Exception {
        return reportRepository.searchReports(query, pageable).map(this::toReportResponse);
    }

    @Transactional(value = "transactionManager", readOnly = true)
    public Page<AdminReportResponse> getReportsByUserId(String userId, Pageable pageable) throws Exception {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        return reportRepository.findByReportedUser(user, pageable)
                .map(this::toAdminReportResponse);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void reportPostByAdmin(User admin, String postId, AdminReportDTO dto) throws Exception {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài viết"));

        reportRepository.updateExistingReportsStatus(postId, ReportType.POST, ReportStatus.RESOLVED, admin, postId);

        Report adminReport = Report.builder()
                .targetId(postId)
                .targetType(ReportType.POST)
                .reporter(admin)
                .reportedUser(post.getUser())
                .resolvedBy(admin)
                .adminNote(dto.getAdminNotes())
                .reason(dto.getReason())
                .status(ReportStatus.RESOLVED)
                .build();
        reportRepository.save(adminReport);

        markPostReportedAndSynchronize(post);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void reportGroupByAdmin(User admin, String groupId, AdminGroupReportDTO dto) throws Exception {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm"));

        GroupMember leader = groupMemberRepository
                .findFirstByGroup_IdAndRoleAndStatus(groupId, MemberRole.LEADER, MemberStatus.APPROVED)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trưởng nhóm"));

        GroupModerationAction action = dto.getAction() != null ? dto.getAction() : GroupModerationAction.ARCHIVE;

        reportRepository.updateExistingReportsStatus(groupId, ReportType.GROUP, ReportStatus.RESOLVED, admin,
                dto.getAdminNotes());

        Report adminReport = Report.builder()
                .targetId(groupId)
                .targetType(ReportType.GROUP)
                .reporter(admin)
                .reportedUser(leader.getUser())
                .resolvedBy(admin)
                .adminNote(dto.getAdminNotes())
                .reason(dto.getReason())
                .description(dto.getDescription())
                .status(ReportStatus.RESOLVED)
                .build();
        reportRepository.save(adminReport);

        if (action == GroupModerationAction.ARCHIVE) {
            group.setStatus(GroupStatus.ARCHIVED);
            publishNotification(
                    admin,
                    leader.getUser(),
                    NotificationType.GROUP_LOCKED,
                    "GROUP",
                    group.getId(),
                    "Nhóm \"" + group.getName() + "\" đã bị khóa/ẩn do vi phạm.");
        }
    }

    private void publishNotification(User actor, User recipient, NotificationType type, String targetType,
            String targetId, String message) {
        if (actor == null || recipient == null || actor.getId().equals(recipient.getId())) {
            return;
        }
        NotificationEvent event = NotificationEvent.builder()
                .recipientId(recipient.getId())
                .recipientName(recipient.getUsername())
                .actorId(actor.getId())
                .type(type)
                .targetType(targetType)
                .targetId(targetId)
                .message(message)
                .build();
        eventPublisher.publishEvent(event);
    }

    private void markPostReportedAndSynchronize(Post post) {
        post.setStatus(ContentStatus.REPORTED);
        postRepository.saveAndFlush(post);

        PostStatusChangedPayload payload = new PostStatusChangedPayload(post.getId(), ContentStatus.REPORTED);

        neo4jSyncEventRepository.save(Neo4jSyncEvent.pending(
                Neo4jEventType.POST_STATUS_CHANGED,
                post.getId(),
                toJson(payload)));
    }

    private void hideActiveCommentTree(Comment comment) {
        if (comment.getStatus() != ContentStatus.ACTIVE) {
            return;
        }

        List<Comment> activeReplies = commentRepository.findByParentComment_IdAndStatus(
                comment.getId(), ContentStatus.ACTIVE);
        for (Comment reply : activeReplies) {
            hideActiveCommentTree(reply);
        }

        comment.setStatus(ContentStatus.HIDDEN);
        commentRepository.save(comment);

        PostCommentChangedPayload payload = new PostCommentChangedPayload(comment.getId());
        neo4jSyncEventRepository.save(Neo4jSyncEvent.pending(
                Neo4jEventType.POST_COMMENT_CHANGED,
                comment.getId(),
                toJson(payload)));
    }

    private ReportResponse toReportResponse(Report report) {
        return ReportResponse.fromEntity(report, getCurrentReportTarget(report));
    }

    private AdminReportResponse toAdminReportResponse(Report report) {
        return AdminReportResponse.fromEntity(report, getCurrentReportTarget(report));
    }

    private ReportTargetResponse getCurrentReportTarget(Report report) {
        if (report == null || report.getTargetType() == null || report.getTargetId() == null) {
            return null;
        }

        return switch (report.getTargetType()) {
            case POST -> postRepository.findById(report.getTargetId())
                    .map(post -> ReportTargetResponse.fromPost(
                            post,
                            postRepository.findImageUrlsByPostId(post.getId())))
                    .orElse(null);
            case COMMENT -> commentRepository.findById(report.getTargetId())
                    .map(ReportTargetResponse::fromComment)
                    .orElse(null);
            case GROUP -> groupRepository.findById(report.getTargetId())
                    .map(ReportTargetResponse::fromGroup)
                    .orElse(null);
        };
    }

}
