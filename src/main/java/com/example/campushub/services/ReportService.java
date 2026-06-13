package com.example.campushub.services;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.campushub.dtos.admin.AdminGroupReportDTO;
import com.example.campushub.dtos.admin.AdminReportDTO;
import com.example.campushub.dtos.users.CreateReportCommentDTO;
import com.example.campushub.dtos.users.CreateReportGroupDTO;
import com.example.campushub.dtos.users.CreateReportPostDTO;
import com.example.campushub.enums.ContentStatus;
import com.example.campushub.enums.GroupModerationAction;
import com.example.campushub.enums.GroupStatus;
import com.example.campushub.enums.MemberRole;
import com.example.campushub.enums.MemberStatus;
import com.example.campushub.enums.NotificationType;
import com.example.campushub.enums.ReportStatus;
import com.example.campushub.enums.ReportType;
import com.example.campushub.enums.UserRole;
import com.example.campushub.events.NotificationEvent;
import com.example.campushub.exceptions.DataNotFoundException;
import com.example.campushub.exceptions.InvalidParamException;
import com.example.campushub.models.jpa.Comment;
import com.example.campushub.models.jpa.Group;
import com.example.campushub.models.jpa.GroupMember;
import com.example.campushub.models.jpa.Post;
import com.example.campushub.models.jpa.Report;
import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.CommentRepository;
import com.example.campushub.repositories.jpa.GroupMemberRepository;
import com.example.campushub.repositories.jpa.GroupRepository;
import com.example.campushub.repositories.jpa.PostRepository;
import com.example.campushub.repositories.jpa.ReportRepository;
import com.example.campushub.repositories.jpa.UserRepository;
import com.example.campushub.repositories.neo4j.PostNeo4jRepository;
import com.example.campushub.responses.ReportResponse;
import com.example.campushub.responses.ReportTargetResponse;
import com.example.campushub.responses.admin.AdminReportResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReportService {
    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final PostNeo4jRepository postNeo4jRepository;
    private final ApplicationEventPublisher eventPublisher;

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

        if (reportRepository.existsByReporterAndTargetTypeAndTargetIdAndStatus(reporter, type, post.getId(), ReportStatus.OPEN)) {
            throw new InvalidParamException("Bạn đã báo cáo bài viết này rồi!");
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
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy nhóm cần báo cáo!"));

        if (reportRepository.existsByReporterAndTargetTypeAndTargetIdAndStatus(reporter, ReportType.GROUP, group.getId(), ReportStatus.OPEN)) {
            throw new InvalidParamException("Bạn đã báo cáo nhóm này và báo cáo đang chờ xử lý!");
        }

        GroupMember leader = groupMemberRepository
                .findFirstByGroup_IdAndRoleAndStatus(groupId, MemberRole.LEADER, MemberStatus.APPROVED)
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy trưởng nhóm"));

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
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy bình luận cần báo cáo!"));

        if (comment.getStatus() != ContentStatus.ACTIVE) {
            throw new DataNotFoundException("Bình luận này không tồn tại hoặc đã bị xóa!");
        }

        if (reportRepository.existsByReporterAndTargetTypeAndTargetIdAndStatus(
                reporter, ReportType.COMMENT, comment.getId(), ReportStatus.OPEN)) {
            throw new InvalidParamException("Bạn đã báo cáo bình luận này và báo cáo đang chờ xử lý!");
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
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy báo cáo!"));
        return report;
    }

    @Transactional(value = "transactionManager", readOnly = true)
    public ReportResponse getReportDetailResponse(String reportId) throws Exception {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy báo cáo!"));
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
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy báo cáo!"));
        report.setStatus(ReportStatus.CLOSED);
        report.setResolvedBy(currentUser);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void handleReportResolution(User currentUser, String reportId, AdminReportDTO dto) throws Exception {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy báo cáo!"));

        String adminNote = dto.getAdminNotes();
        boolean isReporterAdmin = report.getReporter() != null && report.getReporter().getRole() == UserRole.ADMIN;

        if (report.getTargetType() == ReportType.GROUP) {
            Group group = groupRepository.findById(report.getTargetId())
                    .orElseThrow(() -> new DataNotFoundException("Không tìm thấy nhóm liên quan!"));
            group.setStatus(GroupStatus.ARCHIVED);
            reportRepository.updateExistingReportsStatus(report.getTargetId(), ReportType.GROUP, ReportStatus.RESOLVED, currentUser, adminNote);
            
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
            reportRepository.updateExistingReportsStatus(targetCommentId, ReportType.COMMENT, ReportStatus.RESOLVED, currentUser, adminNote);

            Comment comment = commentRepository.findById(targetCommentId)
                    .orElseThrow(() -> new DataNotFoundException("Không tìm thấy bình luận liên quan!"));
            if (comment.getStatus() == ContentStatus.ACTIVE) {
                hideActiveCommentTree(comment);
                decrementParentReplyCounter(comment);
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
            throw new InvalidParamException("Loại báo cáo này chưa được hỗ trợ xử lý tự động");
        }

        String targetPostId = report.getTargetId();
        reportRepository.updateExistingReportsStatus(targetPostId, ReportType.POST, ReportStatus.RESOLVED, currentUser, adminNote);

        Post post = postRepository.findById(report.getTargetId())
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy bài viết liên quan!"));
        post.setStatus(ContentStatus.REPORTED);

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

        try {
            postNeo4jRepository.updatePostStatus(post.getId(), ContentStatus.REPORTED.name());
        } catch (Exception e) {
            throw new Exception("Báo cáo đã được giải quyết nhưng có lỗi khi cập nhật trạng thái bài viết trên Neo4j: "
                    + e.getMessage());
        }
    }

    @Transactional(value = "transactionManager", readOnly = true)
    public Page<ReportResponse> searchReports(String query, Pageable pageable) throws Exception {
        return reportRepository.searchReports(query, pageable).map(this::toReportResponse);
    }

    @Transactional(value = "transactionManager", readOnly = true)
    public Page<AdminReportResponse> getReportsByUserId(String userId, Pageable pageable) throws Exception {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DataNotFoundException("Người dùng không tồn tại"));

        return reportRepository.findByReportedUser(user, pageable)
                .map(this::toAdminReportResponse);
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

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void reportGroupByAdmin(User admin, String groupId, AdminGroupReportDTO dto) throws Exception {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy nhóm"));

        GroupMember leader = groupMemberRepository
                .findFirstByGroup_IdAndRoleAndStatus(groupId, MemberRole.LEADER, MemberStatus.APPROVED)
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy trưởng nhóm"));

        GroupModerationAction action = dto.getAction() != null ? dto.getAction() : GroupModerationAction.ARCHIVE;

        reportRepository.updateExistingReportsStatus(groupId, ReportType.GROUP, ReportStatus.RESOLVED, admin, dto.getAdminNotes());

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
    }

    private void decrementParentReplyCounter(Comment comment) {
        Comment parentComment = comment.getParentComment();
        if (parentComment != null && parentComment.getStatus() == ContentStatus.ACTIVE) {
            int replyCount = parentComment.getReplyCount() != null ? parentComment.getReplyCount() : 0;
            parentComment.setReplyCount(Math.max(0, replyCount - 1));
            commentRepository.save(parentComment);
        }
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
