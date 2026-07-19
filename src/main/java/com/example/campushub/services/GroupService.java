package com.example.campushub.services;

import com.example.campushub.dtos.record.groups.GroupCreatedPayload;
import com.example.campushub.dtos.record.groups.GroupDeletedPayload;
import com.example.campushub.dtos.record.groups.GroupMemberApprovedPayload;
import com.example.campushub.dtos.record.groups.GroupMemberRemovedPayload;
import com.example.campushub.dtos.record.groups.GroupNameUpdatedPayload;
import com.example.campushub.dtos.users.CreateGroupDTO;
import com.example.campushub.enums.GroupStatus;
import com.example.campushub.enums.MemberRole;
import com.example.campushub.enums.MemberStatus;
import com.example.campushub.enums.Neo4jEventType;
import com.example.campushub.enums.NotificationType;
import com.example.campushub.enums.ReportStatus;
import com.example.campushub.enums.ReportType;
import com.example.campushub.enums.UserRole;
import com.example.campushub.enums.UserStatus;
import com.example.campushub.events.NotificationEvent;
import com.example.campushub.exceptions.ResourceNotFoundException;
import com.example.campushub.exceptions.ForbiddenException;
import com.example.campushub.exceptions.BadRequestException;
import com.example.campushub.models.jpa.Group;
import com.example.campushub.models.jpa.GroupMember;
import com.example.campushub.models.jpa.GroupMemberId;
import com.example.campushub.models.jpa.Neo4jSyncEvent;
import com.example.campushub.models.jpa.Report;
import com.example.campushub.models.jpa.User;
import com.example.campushub.models.neo4j.GroupNode;
import com.example.campushub.repositories.jpa.GroupMemberRepository;
import com.example.campushub.repositories.jpa.GroupRepository;
import com.example.campushub.repositories.jpa.Neo4jSyncEventRepository;
import com.example.campushub.repositories.jpa.ReportRepository;
import com.example.campushub.repositories.jpa.UserRepository;
import com.example.campushub.repositories.neo4j.GroupNeo4jRepository;
import com.example.campushub.responses.GroupMemberResponse;
import com.example.campushub.responses.GroupResponse;
import com.example.campushub.responses.GroupStatusResponse;
import com.example.campushub.responses.ReportResponse;
import com.example.campushub.responses.ReportTargetResponse;
import lombok.RequiredArgsConstructor;
import net.datafaker.Faker;
import tools.jackson.databind.ObjectMapper;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final GroupNeo4jRepository groupNeo4jRepository;
    private final Neo4jSyncEventRepository neo4jSyncEventRepository;
    private final ObjectMapper objectMapper;
    private final FileUploadService fileUploadService;
    private final ApplicationEventPublisher eventPublisher;
    private final Faker faker;

    private String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert object to JSON", e);
        }
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public GroupResponse createGroup(User user, CreateGroupDTO dto) {
        GroupMember adminMember = createGroupWithLeader(user, dto);
        Group group = adminMember.getGroup();
        return GroupResponse.fromGroup(group, adminMember);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int seedGroups(User user, int count) {
        List<String> groupTypes = List.of(
                "CLB",
                "Cộng đồng",
                "Nhóm học tập",
                "Hội sinh viên",
                "Không gian",
                "Diễn đàn",
                "Đội dự án",
                "Ban tổ chức",
                "Workshop",
                "Mentoring",
                "Study Hub",
                "Research Lab",
                "Career Network",
                "Sân chơi",
                "Câu lạc bộ");
        List<String> topics = List.of(
                "Lập trình",
                "Thiết kế UI UX",
                "Ôn thi cuối kỳ",
                "Tiếng Anh",
                "Nghiên cứu khoa học",
                "Data và AI",
                "Tình nguyện",
                "Thể thao",
                "Việc làm thực tập",
                "Sách và Podcast",
                "An toàn thông tin",
                "Kỹ năng mềm",
                "Khởi nghiệp",
                "Marketing",
                "Tài chính cá nhân",
                "Nhiếp ảnh",
                "Âm nhạc",
                "Du lịch",
                "Game Development",
                "Mobile App",
                "Web Development",
                "Cloud Computing",
                "DevOps",
                "Blockchain",
                "Robotics",
                "Toán ứng dụng",
                "Kinh tế",
                "Truyền thông",
                "Sự kiện sinh viên",
                "Trao đổi tài liệu",
                "Học bổng",
                "Cựu sinh viên",
                "Định hướng nghề nghiệp",
                "Sức khỏe tinh thần",
                "Môi trường xanh");
        List<User> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE);
        if (activeUsers.isEmpty()) {
            throw new BadRequestException("Cannot seed groups because no active users exist");
        }

        int successCount = 0;
        for (int i = 0; i < count; i++) {
            User leader = randomElement(activeUsers);
            String randomSeed = faker.internet().uuid();
            String groupType = faker.options().nextElement(groupTypes);
            String topic = faker.options().nextElement(topics);
            String groupName = groupType + " " + topic;
            if (groupRepository.existsByNameIgnoreCase(groupName)) {
                continue;
            }

            String description = "Nhóm dành cho sinh viên quan tâm đến " + topic.toLowerCase()
                    + ", cùng chia sẻ tài liệu, kinh nghiệm và hoạt động trong CampusHub.";
            String avatarUrl = "https://api.dicebear.com/9.x/shapes/svg?seed=" + randomSeed;
            String coverUrl = "https://picsum.photos/seed/" + randomSeed + "/1200/400";

            Group group = Group.builder()
                    .name(groupName)
                    .description(description)
                    .avatarUrl(avatarUrl)
                    .coverUrl(coverUrl)
                    .memberCount(1)
                    .status(GroupStatus.ACTIVE)
                    .build();

            group = groupRepository.save(group);

            GroupMember leaderMember = GroupMember.builder()
                    .id(new GroupMemberId(group.getId(), leader.getId()))
                    .group(group)
                    .user(leader)
                    .role(MemberRole.LEADER)
                    .status(MemberStatus.APPROVED)
                    .joinedAt(LocalDateTime.now())
                    .build();

            groupMemberRepository.save(leaderMember);

            GroupCreatedPayload payload = new GroupCreatedPayload(
                    group.getId(),
                    leader.getId(),
                    group.getName());

            neo4jSyncEventRepository.save(Neo4jSyncEvent.pending(
                    Neo4jEventType.GROUP_CREATED, group.getId(), toJson(payload)));

            successCount++;
        }
        return successCount;
    }

    private GroupMember createGroupWithLeader(User user, CreateGroupDTO dto) {
        Group group = Group.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .memberCount(1)
                .status(GroupStatus.ACTIVE)
                .build();

        group = groupRepository.saveAndFlush(group);

        GroupMember leaderMember = GroupMember.builder()
                .id(new GroupMemberId(group.getId(), user.getId()))
                .group(group)
                .user(user)
                .role(MemberRole.LEADER)
                .status(MemberStatus.APPROVED)
                .joinedAt(LocalDateTime.now())
                .build();

        groupMemberRepository.saveAndFlush(leaderMember);

        GroupCreatedPayload payload = new GroupCreatedPayload(
                group.getId(),
                user.getId(),
                group.getName());

        neo4jSyncEventRepository.save(Neo4jSyncEvent.pending(
                Neo4jEventType.GROUP_CREATED, group.getId(), toJson(payload)));
        return leaderMember;
    }

    public List<GroupResponse> getAllGroups(User currentUser) {
        Map<String, GroupMember> currentUserMembers = currentUser == null
                ? Map.of()
                : groupMemberRepository.findByUser(currentUser).stream()
                        .collect(Collectors.toMap(
                                member -> member.getId().getGroupId(),
                                member -> member,
                                (existing, replacement) -> existing));

        List<Group> visibleGroups = groupRepository.findAll().stream()
                .filter(group -> group.getStatus() != GroupStatus.DELETED)
                .filter(group -> group.getStatus() == GroupStatus.ACTIVE
                        || currentUserMembers.containsKey(group.getId()))
                .collect(Collectors.toList());
        Map<String, GroupMember> leaderMembers = findLeaderMembersByGroupIds(
                visibleGroups.stream().map(Group::getId).collect(Collectors.toList()));

        return visibleGroups.stream()
                .map(group -> GroupResponse.fromGroup(group, currentUserMembers.get(group.getId()),
                        leaderMembers.get(group.getId())))
                .collect(Collectors.toList());
    }

    public GroupResponse getGroupById(User currentUser, String groupId) throws Exception {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm"));
        if (group.getStatus() == GroupStatus.DELETED) {
            throw new ResourceNotFoundException("Nhóm không tồn tại hoặc đã bị xóa");
        }
        GroupMember currentUserMember = findCurrentUserMember(currentUser, groupId);
        GroupMember leaderMember = findLeaderMember(groupId);

        return GroupResponse.fromGroup(group, currentUserMember, leaderMember);
    }

    @Transactional(value = "transactionManager", readOnly = true)
    public GroupStatusResponse getGroupStatus(String groupId) throws Exception {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm"));
        if (group.getStatus() == GroupStatus.DELETED) {
            throw new ResourceNotFoundException("Nhóm không tồn tại hoặc đã bị xóa");
        }
        List<Report> groupReports = reportRepository.findAllByTargetIdAndTargetTypeOrderByCreatedAtDesc(groupId,
                ReportType.GROUP);
        List<ReportResponse> reports = groupReports.stream()
                .filter(report -> report.getReporter().getRole() == UserRole.ADMIN)
                .filter(report -> report.getStatus() == ReportStatus.RESOLVED)
                .map(report -> ReportResponse.fromEntity(report, ReportTargetResponse.fromGroup(group)))
                .collect(Collectors.toList());
        String adminNotes = groupReports.stream()
                .map(Report::getAdminNote)
                .filter(note -> note != null && !note.isBlank())
                .findFirst()
                .orElse(null);

        return GroupStatusResponse.builder()
                .status(group.getStatus())
                .reportCount(reports.size())
                .adminNotes(adminNotes)
                .reports(reports)
                .build();
    }

    public Page<GroupMemberResponse> getGroupMembers(String groupId, Pageable pageable) throws Exception {
        if (!groupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("Không tìm thấy nhóm");
        }

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm"));
        if (group.getStatus() == GroupStatus.DELETED) {
            throw new ResourceNotFoundException("Nhóm không tồn tại hoặc đã bị xóa");
        }

        return groupMemberRepository.findByGroup_IdAndStatus(groupId, MemberStatus.APPROVED, pageable)
                .map(GroupMemberResponse::fromGroupMember);
    }

    public Page<GroupResponse> getAdminGroups(String query, String status, Pageable pageable) {
        GroupStatus groupStatus = parseGroupStatus(status);
        String normalizedQuery = query == null ? null : query.trim();

        Page<Group> groups = groupRepository.searchAdminGroups(normalizedQuery, groupStatus, pageable);
        Map<String, GroupMember> leaderMembers = findLeaderMembersByGroupIds(
                groups.getContent().stream().map(Group::getId).collect(Collectors.toList()));

        return groups.map(group -> GroupResponse.fromGroup(group, null, leaderMembers.get(group.getId())));
    }

    private GroupStatus parseGroupStatus(String status) {
        if (status == null || status.isBlank() || status.equalsIgnoreCase("ALL")) {
            return null;
        }

        try {
            return GroupStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Trạng thái nhóm không hợp lệ: " + status);
        }
    }

    @Transactional(value = "transactionManager", readOnly = true)
    public Page<GroupMemberResponse> getPendingGroupMembers(User currentUser, String groupId, Pageable pageable)
            throws Exception {
        GroupMemberId currentUserId = new GroupMemberId(groupId, currentUser.getId());
        GroupMember currentUserMember = groupMemberRepository.findById(currentUserId)
                .orElseThrow(() -> new ForbiddenException("Bạn không phải là thành viên của nhóm này"));

        assertGroupActive(currentUserMember.getGroup());

        if (currentUserMember.getRole() != MemberRole.LEADER) {
            throw new ForbiddenException("Chỉ trưởng nhóm mới có quyền xem danh sách chờ duyệt");
        }

        return groupMemberRepository.findByGroup_IdAndStatus(groupId, MemberStatus.PENDING, pageable)
                .map(GroupMemberResponse::fromGroupMember);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public GroupResponse updateGroupAvatar(User currentUser, String groupId, MultipartFile file) throws Exception {
        GroupMemberId currentUserId = new GroupMemberId(groupId, currentUser.getId());
        GroupMember currentUserMember = groupMemberRepository.findById(currentUserId)
                .orElseThrow(() -> new ForbiddenException("Bạn không phải là thành viên của nhóm này"));

        assertGroupActive(currentUserMember.getGroup());

        if (currentUserMember.getRole() != MemberRole.LEADER) {
            throw new ForbiddenException("Chỉ trưởng nhóm mới có quyền thay đổi ảnh đại diện nhóm");
        }

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm"));

        String avatarUrl = fileUploadService.uploadFile(file, "groups/avatars");
        group.setAvatarUrl(avatarUrl);
        group = groupRepository.save(group);

        return GroupResponse.fromGroup(group, currentUserMember);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public GroupResponse updateGroupCover(User currentUser, String groupId, MultipartFile file) throws Exception {
        GroupMemberId currentUserId = new GroupMemberId(groupId, currentUser.getId());
        GroupMember currentUserMember = groupMemberRepository.findById(currentUserId)
                .orElseThrow(() -> new ForbiddenException("Bạn không phải là thành viên của nhóm này"));

        assertGroupActive(currentUserMember.getGroup());

        if (currentUserMember.getRole() != MemberRole.LEADER) {
            throw new ForbiddenException("Chỉ trưởng nhóm mới có quyền thay đổi ảnh bìa nhóm");
        }

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm"));

        String coverUrl = fileUploadService.uploadFile(file, "groups/covers");
        group.setCoverUrl(coverUrl);
        group = groupRepository.save(group);

        return GroupResponse.fromGroup(group, currentUserMember);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void joinGroup(User user, String groupId) throws Exception {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm này"));

        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw new Exception("Nhóm này không còn hoạt động");
        }

        GroupMemberId id = new GroupMemberId(group.getId(), user.getId());

        GroupMember member = groupMemberRepository.findById(id).orElse(null);
        if (member != null && member.getStatus() == MemberStatus.APPROVED) {
            throw new RuntimeException("Bạn đã là thành viên của nhóm này");
        }
        if (member != null && member.getStatus() == MemberStatus.PENDING) {
            throw new RuntimeException("Bạn đang chờ duyệt vào nhóm này");
        }

        if (member != null) {
            member.setStatus(MemberStatus.PENDING);
            member.setRole(MemberRole.MEMBER);
            member.setJoinedAt(LocalDateTime.now());
        } else {
            member = GroupMember.builder()
                    .id(id)
                    .group(group)
                    .user(user)
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.PENDING)
                    .joinedAt(LocalDateTime.now())
                    .build();
        }

        groupMemberRepository.save(member);
        GroupMember leaderMember = findLeaderMember(groupId);
        if (leaderMember != null) {
            publishGroupNotification(
                    user,
                    leaderMember.getUser(),
                    NotificationType.GROUP_JOIN_REQUEST,
                    group,
                    user.getFullName() + " đã yêu cầu tham gia nhóm \"" + group.getName() + "\".");
        }
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void approveJoinRequest(User currentUser, String groupId, String targetUserId) throws Exception {
        // Serialize changes to this group's membership count.
        Group group = groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm"));

        GroupMemberId currentUserId = new GroupMemberId(groupId, currentUser.getId());
        GroupMember currentUserMember = groupMemberRepository.findById(currentUserId)
                .orElseThrow(() -> new ForbiddenException("Bạn không phải là thành viên của nhóm này"));

        assertGroupActive(group);

        if (currentUserMember.getRole() != MemberRole.LEADER) {
            throw new ForbiddenException("Chỉ trưởng nhóm mới có quyền duyệt thành viên");
        }

        GroupMemberId targetId = new GroupMemberId(groupId, targetUserId);
        GroupMember targetMember = groupMemberRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu tham gia của người dùng này"));

        if (targetMember.getStatus() != MemberStatus.PENDING) {
            throw new BadRequestException("Yêu cầu tham gia không còn chờ duyệt");
        }

        targetMember.setStatus(MemberStatus.APPROVED);
        targetMember.setJoinedAt(LocalDateTime.now());

        groupMemberRepository.save(targetMember);

        group.setMemberCount(group.getMemberCount() + 1);
        groupRepository.save(group);

        GroupMemberApprovedPayload payload = new GroupMemberApprovedPayload(
                group.getId(),
                targetUserId);

        neo4jSyncEventRepository.save(Neo4jSyncEvent.pending(
                Neo4jEventType.GROUP_MEMBER_APPROVED, group.getId(), toJson(payload)));

        publishGroupNotification(
                currentUser,
                targetMember.getUser(),
                NotificationType.GROUP_JOIN_APPROVED,
                group,
                "Yêu cầu tham gia nhóm \"" + group.getName() + "\" của bạn đã được chấp nhận.");
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void rejectJoinRequest(User currentUser, String groupId, String targetUserId) throws Exception {
        GroupMemberId currentUserId = new GroupMemberId(groupId, currentUser.getId());
        GroupMember currentUserMember = groupMemberRepository.findById(currentUserId)
                .orElseThrow(() -> new ForbiddenException("Bạn không phải là thành viên của nhóm này"));

        assertGroupActive(currentUserMember.getGroup());

        if (currentUserMember.getRole() != MemberRole.LEADER) {
            throw new ForbiddenException("Chỉ trưởng nhóm mới có quyền từ chối thành viên");
        }

        GroupMemberId targetId = new GroupMemberId(groupId, targetUserId);
        GroupMember targetMember = groupMemberRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu tham gia của người dùng này"));

        if (targetMember.getStatus() == MemberStatus.APPROVED) {
            throw new Exception("Người dùng này đã là thành viên của nhóm");
        }

        targetMember.setStatus(MemberStatus.REJECTED);
        groupMemberRepository.save(targetMember);
        Group group = currentUserMember.getGroup();
        publishGroupNotification(
                currentUser,
                targetMember.getUser(),
                NotificationType.GROUP_JOIN_REJECTED,
                group,
                "Yêu cầu tham gia nhóm \"" + group.getName() + "\" của bạn đã bị từ chối.");
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void kickMember(User currentUser, String groupId, String targetUserId) throws Exception {
        Group group = groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm"));

        GroupMemberId currentUserId = new GroupMemberId(groupId, currentUser.getId());
        GroupMember currentUserMember = groupMemberRepository.findById(currentUserId)
                .orElseThrow(() -> new ForbiddenException("Bạn không phải là thành viên của nhóm này"));

        assertGroupActive(group);

        if (currentUserMember.getRole() != MemberRole.LEADER) {
            throw new ForbiddenException("Chỉ trưởng nhóm mới có quyền đuổi thành viên");
        }

        if (currentUser.getId().equals(targetUserId)) {
            throw new Exception("Bạn không thể tự đuổi chính mình");
        }

        GroupMemberId targetId = new GroupMemberId(groupId, targetUserId);
        GroupMember targetMember = groupMemberRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thành viên này trong nhóm"));

        if (targetMember.getStatus() != MemberStatus.APPROVED) {
            throw new Exception("Người dùng này chưa phải là thành viên chính thức của nhóm");
        }

        groupMemberRepository.delete(targetMember);

        group.setMemberCount(group.getMemberCount() - 1);
        groupRepository.save(group);

        GroupMemberRemovedPayload payload = new GroupMemberRemovedPayload(
                group.getId(),
                targetUserId);

        neo4jSyncEventRepository.save(Neo4jSyncEvent.pending(
                Neo4jEventType.GROUP_MEMBER_REMOVED, groupId, toJson(payload)));

        publishGroupNotification(
                currentUser,
                targetMember.getUser(),
                NotificationType.GROUP_MEMBER_KICKED,
                group,
                "Bạn đã bị xóa khỏi nhóm \"" + group.getName() + "\".");
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void leaveGroup(User currentUser, String groupId) throws Exception {
        Group group = groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm"));

        GroupMemberId currentUserId = new GroupMemberId(groupId, currentUser.getId());
        GroupMember currentUserMember = groupMemberRepository.findById(currentUserId)
                .orElseThrow(() -> new ForbiddenException("Bạn chưa tham gia nhóm này"));

        if (currentUserMember.getRole() == MemberRole.LEADER) {
            throw new ForbiddenException(
                    "Trưởng nhóm không thể tự rời nhóm. Hãy nhường quyền cho người khác trước.");
        }

        boolean wasApprovedMember = currentUserMember.getStatus() == MemberStatus.APPROVED;
        groupMemberRepository.delete(currentUserMember);

        if (wasApprovedMember) {
            group.setMemberCount(group.getMemberCount() - 1);
            groupRepository.save(group);

            GroupMemberRemovedPayload payload = new GroupMemberRemovedPayload(
                    group.getId(),
                    currentUser.getId());

            neo4jSyncEventRepository.save(Neo4jSyncEvent.pending(
                    Neo4jEventType.GROUP_MEMBER_REMOVED, groupId, toJson(payload)));
        }
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void deleteGroup(User currentUser, String groupId) throws Exception {
        Group group = groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
        GroupMemberId currentUserId = new GroupMemberId(groupId, currentUser.getId());
        GroupMember currentUserMember = groupMemberRepository.findById(currentUserId)
                .orElseThrow(() -> new ForbiddenException("Bạn không phải là thành viên của nhóm này"));

        assertGroupActive(currentUserMember.getGroup());

        if (currentUserMember.getRole() != MemberRole.LEADER) {
            throw new ForbiddenException("Chỉ trưởng nhóm mới có quyền xóa nhóm");
        }

        assertGroupActive(group);
        group.setStatus(GroupStatus.DELETED);
        groupRepository.saveAndFlush(group);

        GroupDeletedPayload payload = new GroupDeletedPayload(group.getId());

        neo4jSyncEventRepository.save(Neo4jSyncEvent.pending(
                Neo4jEventType.GROUP_DELETED, groupId, toJson(payload)));
    }

    private GroupMember findCurrentUserMember(User currentUser, String groupId) {
        if (currentUser == null) {
            return null;
        }

        return groupMemberRepository.findById(new GroupMemberId(groupId, currentUser.getId())).orElse(null);
    }

    private GroupMember findLeaderMember(String groupId) {
        return groupMemberRepository
                .findFirstByGroup_IdAndRoleAndStatus(groupId, MemberRole.LEADER, MemberStatus.APPROVED)
                .orElse(null);
    }

    private Map<String, GroupMember> findLeaderMembersByGroupIds(List<String> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return Map.of();
        }

        return groupMemberRepository
                .findByGroup_IdInAndRoleAndStatus(groupIds, MemberRole.LEADER, MemberStatus.APPROVED)
                .stream()
                .collect(Collectors.toMap(
                        member -> member.getId().getGroupId(),
                        member -> member,
                        (existing, replacement) -> existing));
    }

    private void assertGroupActive(Group group) {
        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw new ForbiddenException("Nhóm này đã bị lưu trữ và không còn cho phép thao tác mới");
        }
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public GroupResponse adminLockGroup(User admin, String groupId) throws Exception {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm"));
        if (group.getStatus() == GroupStatus.DELETED) {
            throw new BadRequestException("Nhóm này đã bị xóa");
        }

        group.setStatus(GroupStatus.ARCHIVED);
        group = groupRepository.save(group);
        GroupMember leaderMember = findLeaderMember(groupId);
        notifyLeader(admin, leaderMember, NotificationType.GROUP_LOCKED, group,
                "Nhóm \"" + group.getName() + "\" đã bị khóa/ẩn do vi phạm.");
        return GroupResponse.fromGroup(group, null, leaderMember);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public GroupResponse adminUnlockGroup(User admin, String groupId) throws Exception {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm"));
        if (group.getStatus() == GroupStatus.DELETED) {
            throw new BadRequestException("Nhóm này đã bị xóa");
        }

        group.setStatus(GroupStatus.ACTIVE);
        group = groupRepository.save(group);
        GroupMember leaderMember = findLeaderMember(groupId);
        notifyLeader(admin, leaderMember, NotificationType.GROUP_UNLOCKED, group,
                "Nhóm \"" + group.getName() + "\" đã được mở lại.");
        return GroupResponse.fromGroup(group, null, leaderMember);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public GroupResponse updateGroupName(User currentUser, String groupId, String name) throws Exception {
        Group group = groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
        GroupMemberId currentUserId = new GroupMemberId(groupId, currentUser.getId());
        GroupMember currentUserMember = groupMemberRepository.findById(currentUserId)
                .orElseThrow(() -> new ForbiddenException("Bạn không phải là thành viên của nhóm này"));

        assertGroupActive(currentUserMember.getGroup());

        if (currentUserMember.getRole() != MemberRole.LEADER) {
            throw new ForbiddenException("Chỉ trưởng nhóm mới có quyền thay đổi tên nhóm");
        }

        assertGroupActive(group);
        group.setName(name);
        groupRepository.saveAndFlush(group);

        GroupNameUpdatedPayload payload = new GroupNameUpdatedPayload(group.getId(), name);

        neo4jSyncEventRepository.save(Neo4jSyncEvent.pending(
                Neo4jEventType.GROUP_NAME_UPDATED, groupId, toJson(payload)));

        notifyApprovedMembers(
                currentUser,
                group,
                NotificationType.GROUP_NAME_UPDATED,
                "Nhóm của bạn đã đổi tên thành \"" + group.getName() + "\".");

        return GroupResponse.fromGroup(group, currentUserMember);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public GroupResponse updateGroupDescription(User currentUser, String groupId, String description) throws Exception {
        GroupMemberId currentUserId = new GroupMemberId(groupId, currentUser.getId());
        GroupMember currentUserMember = groupMemberRepository.findById(currentUserId)
                .orElseThrow(() -> new ForbiddenException("Bạn không phải là thành viên của nhóm này"));

        assertGroupActive(currentUserMember.getGroup());

        if (currentUserMember.getRole() != MemberRole.LEADER) {
            throw new ForbiddenException("Chỉ trưởng nhóm mới có quyền thay đổi mô tả nhóm");
        }

        Group group = currentUserMember.getGroup();
        group.setDescription(description);
        groupRepository.save(group);

        return GroupResponse.fromGroup(group, currentUserMember);
    }

    private void notifyLeader(User actor, GroupMember leaderMember, NotificationType type, Group group,
            String message) {
        if (leaderMember == null) {
            return;
        }
        publishGroupNotification(actor, leaderMember.getUser(), type, group, message);
    }

    private void notifyApprovedMembers(User actor, Group group, NotificationType type, String message) {
        groupMemberRepository.findByGroup_IdAndStatus(group.getId(), MemberStatus.APPROVED).stream()
                .map(GroupMember::getUser)
                .filter(member -> !member.getId().equals(actor.getId()))
                .forEach(member -> publishGroupNotification(actor, member, type, group, message));
    }

    private void publishGroupNotification(User actor, User recipient, NotificationType type, Group group,
            String message) {
        if (actor == null || recipient == null || actor.getId().equals(recipient.getId())) {
            return;
        }
        NotificationEvent event = NotificationEvent.builder()
                .recipientId(recipient.getId())
                .recipientName(recipient.getUsername())
                .actorId(actor.getId())
                .type(type)
                .targetType("GROUP")
                .targetId(group.getId())
                .message(message)
                .build();
        eventPublisher.publishEvent(event);
    }

    private <T> T randomElement(List<T> values) {
        return values.get(faker.random().nextInt(values.size()));
    }
}
