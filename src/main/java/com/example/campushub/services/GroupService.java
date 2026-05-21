package com.example.campushub.services;

import com.example.campushub.dtos.users.CreateGroupDTO;
import com.example.campushub.enums.GroupStatus;
import com.example.campushub.enums.MemberRole;
import com.example.campushub.enums.MemberStatus;
import com.example.campushub.enums.ReportStatus;
import com.example.campushub.enums.ReportType;
import com.example.campushub.enums.UserRole;
import com.example.campushub.exceptions.DataNotFoundException;
import com.example.campushub.exceptions.ForbiddenAccessException;
import com.example.campushub.models.jpa.Group;
import com.example.campushub.models.jpa.GroupMember;
import com.example.campushub.models.jpa.GroupMemberId;
import com.example.campushub.models.jpa.Report;
import com.example.campushub.models.jpa.User;
import com.example.campushub.models.neo4j.GroupNode;
import com.example.campushub.repositories.jpa.GroupMemberRepository;
import com.example.campushub.repositories.jpa.GroupRepository;
import com.example.campushub.repositories.jpa.ReportRepository;
import com.example.campushub.repositories.neo4j.GroupNeo4jRepository;
import com.example.campushub.responses.GroupMemberResponse;
import com.example.campushub.responses.GroupResponse;
import com.example.campushub.responses.GroupStatusResponse;
import com.example.campushub.responses.ReportResponse;
import lombok.RequiredArgsConstructor;
import net.datafaker.Faker;
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
    private final GroupNeo4jRepository groupNeo4jRepository;
    private final FileUploadService fileUploadService;
    private final Faker faker;

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public GroupResponse createGroup(User user, CreateGroupDTO dto) {
        GroupMember adminMember = createGroupWithLeader(user, dto);
        Group group = adminMember.getGroup();
        return GroupResponse.fromGroup(group, adminMember);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int seedGroups(User user, int count){
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
        int successCount = 0;
        for (int i = 0; i < count; i++) {
            try {
                String randomSeed = faker.internet().uuid();
                String groupType = faker.options().nextElement(groupTypes);
                String topic = faker.options().nextElement(topics);
                String groupName = groupType + " " + topic + " " + randomSeed.substring(0, 8);
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
                try {
                    GroupNode groupNode = GroupNode.builder()
                            .id(group.getId())
                            .name(group.getName())
                            .build();
                    groupNeo4jRepository.save(groupNode);
                    groupNeo4jRepository.addUserToGroup(user.getId(), group.getId());
                } catch (Exception e) {
                    throw new RuntimeException("Tạo nhóm thất bại tại Neo4j", e);
                }

                GroupMember leaderMember = GroupMember.builder()
                        .id(new GroupMemberId(group.getId(), user.getId()))
                        .group(group)
                        .user(user)
                        .role(MemberRole.LEADER)
                        .status(MemberStatus.APPROVED)
                        .joinedAt(LocalDateTime.now())
                        .build();

                groupMemberRepository.save(leaderMember);
                successCount++;
            } catch (Exception e) {
                // Bỏ qua lỗi và tiếp tục tạo nhóm tiếp theo
            }
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

        group = groupRepository.save(group);

        GroupNode groupNode = GroupNode.builder()
                .id(group.getId())
                .name(group.getName())
                .build();
        groupNeo4jRepository.save(groupNode);

        GroupMember leaderMember = GroupMember.builder()
                .id(new GroupMemberId(group.getId(), user.getId()))
                .group(group)
                .user(user)
                .role(MemberRole.LEADER)
                .status(MemberStatus.APPROVED)
                .joinedAt(LocalDateTime.now())
                .build();

        groupMemberRepository.save(leaderMember);
        groupNeo4jRepository.addUserToGroup(user.getId(), group.getId());

        return leaderMember;
    }

    public List<GroupResponse> getAllGroups(User currentUser) {
        Map<String, GroupMember> currentUserMembers = currentUser == null
                ? Map.of()
                : groupMemberRepository.findByUser(currentUser).stream()
                .collect(Collectors.toMap(
                        member -> member.getId().getGroupId(),
                        member -> member,
                        (existing, replacement) -> existing
                ));

        List<Group> visibleGroups = groupRepository.findAll().stream()
                .filter(group -> group.getStatus() == GroupStatus.ACTIVE || currentUserMembers.containsKey(group.getId()))
                .collect(Collectors.toList());
        Map<String, GroupMember> leaderMembers = findLeaderMembersByGroupIds(
                visibleGroups.stream().map(Group::getId).collect(Collectors.toList())
        );

        return visibleGroups.stream()
                .map(group -> GroupResponse.fromGroup(group, currentUserMembers.get(group.getId()), leaderMembers.get(group.getId())))
                .collect(Collectors.toList());
    }

    public GroupResponse getGroupById(User currentUser, String groupId) throws Exception {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy nhóm"));
        GroupMember currentUserMember = findCurrentUserMember(currentUser, groupId);
        GroupMember leaderMember = findLeaderMember(groupId);

        return GroupResponse.fromGroup(group, currentUserMember, leaderMember);
    }

    @Transactional(value = "transactionManager", readOnly = true)
    public GroupStatusResponse getGroupStatus(String groupId) throws Exception {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy nhóm"));
        List<Report> groupReports = reportRepository.findAllByTargetIdAndTargetTypeOrderByCreatedAtDesc(groupId, ReportType.GROUP);
        List<ReportResponse> reports = groupReports.stream()
                .filter(report -> report.getReporter().getRole() != UserRole.ADMIN)
                .filter(report -> report.getStatus() == ReportStatus.RESOLVED)
                .map(ReportResponse::fromEntity)
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
            throw new DataNotFoundException("Không tìm thấy nhóm");
        }

        return groupMemberRepository.findByGroup_IdAndStatus(groupId, MemberStatus.APPROVED, pageable)
                .map(GroupMemberResponse::fromGroupMember);
    }

    public Page<GroupResponse> getAdminGroups(String query, String status, Pageable pageable) {
        GroupStatus groupStatus = parseGroupStatus(status);
        String normalizedQuery = query == null ? null : query.trim();

        Page<Group> groups = groupRepository.searchAdminGroups(normalizedQuery, groupStatus, pageable);
        Map<String, GroupMember> leaderMembers = findLeaderMembersByGroupIds(
                groups.getContent().stream().map(Group::getId).collect(Collectors.toList())
        );

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

    @Transactional(value= "transactionManager", readOnly = true)
    public Page<GroupMemberResponse> getPendingGroupMembers(User currentUser, String groupId, Pageable pageable) throws Exception {
        GroupMemberId currentUserId = new GroupMemberId(groupId, currentUser.getId());
        GroupMember currentUserMember = groupMemberRepository.findById(currentUserId)
                .orElseThrow(() -> new ForbiddenAccessException("Bạn không phải là thành viên của nhóm này"));

        assertGroupActive(currentUserMember.getGroup());

        if (currentUserMember.getRole() != MemberRole.LEADER) {
            throw new ForbiddenAccessException("Chỉ trưởng nhóm mới có quyền xem danh sách chờ duyệt");
        }

        return groupMemberRepository.findByGroup_IdAndStatus(groupId, MemberStatus.PENDING, pageable)
                .map(GroupMemberResponse::fromGroupMember);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public GroupResponse updateGroupAvatar(User currentUser, String groupId, MultipartFile file) throws Exception {
        GroupMemberId currentUserId = new GroupMemberId(groupId, currentUser.getId());
        GroupMember currentUserMember = groupMemberRepository.findById(currentUserId)
                .orElseThrow(() -> new Exception("Bạn không phải là thành viên của nhóm này"));

        assertGroupActive(currentUserMember.getGroup());

        if (currentUserMember.getRole() != MemberRole.LEADER) {
            throw new ForbiddenAccessException("Chỉ trưởng nhóm mới có quyền thay đổi ảnh đại diện nhóm");
        }

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy nhóm"));

        String avatarUrl = fileUploadService.uploadFile(file, "groups/avatars");
        group.setAvatarUrl(avatarUrl);
        group = groupRepository.save(group);

        return GroupResponse.fromGroup(group, currentUserMember);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public GroupResponse updateGroupCover(User currentUser, String groupId, MultipartFile file) throws Exception {
        GroupMemberId currentUserId = new GroupMemberId(groupId, currentUser.getId());
        GroupMember currentUserMember = groupMemberRepository.findById(currentUserId)
                .orElseThrow(() -> new Exception("Bạn không phải là thành viên của nhóm này"));

        assertGroupActive(currentUserMember.getGroup());

        if (currentUserMember.getRole() != MemberRole.LEADER) {
            throw new ForbiddenAccessException("Chỉ trưởng nhóm mới có quyền thay đổi ảnh bìa nhóm");
        }

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy nhóm"));

        String coverUrl = fileUploadService.uploadFile(file, "groups/covers");
        group.setCoverUrl(coverUrl);
        group = groupRepository.save(group);

        return GroupResponse.fromGroup(group, currentUserMember);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void joinGroup(User user, String groupId) throws Exception {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy nhóm này"));

        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw new Exception("Nhóm này không còn hoạt động");
        }

        GroupMemberId id = new GroupMemberId(group.getId(), user.getId());

        if (groupMemberRepository.existsById(id)) {
            throw new RuntimeException("Bạn đã tham gia hoặc đang chờ duyệt vào nhóm này");
        }

        GroupMember newMember = GroupMember.builder()
                .id(id)
                .group(group)
                .user(user)
                .role(MemberRole.MEMBER)
                .status(MemberStatus.PENDING)
                .build();

        groupMemberRepository.save(newMember);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void approveJoinRequest(User currentUser, String groupId, String targetUserId) throws Exception {
        GroupMemberId currentUserId = new GroupMemberId(groupId, currentUser.getId());
        GroupMember currentUserMember = groupMemberRepository.findById(currentUserId)
                .orElseThrow(() -> new Exception("Bạn không phải là thành viên của nhóm này"));

        assertGroupActive(currentUserMember.getGroup());

        if (currentUserMember.getRole() != MemberRole.LEADER) {
            throw new ForbiddenAccessException("Chỉ trưởng nhóm mới có quyền duyệt thành viên");
        }

        GroupMemberId targetId = new GroupMemberId(groupId, targetUserId);
        GroupMember targetMember = groupMemberRepository.findById(targetId)
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy yêu cầu tham gia của người dùng này"));

        if (targetMember.getStatus() == MemberStatus.APPROVED) {
            throw new Exception("Người dùng này đã là thành viên của nhóm");
        }

        targetMember.setStatus(MemberStatus.APPROVED);
        targetMember.setJoinedAt(LocalDateTime.now());

        groupMemberRepository.save(targetMember);

        Group group = currentUserMember.getGroup();
        group.setMemberCount(group.getMemberCount() + 1);
        groupRepository.save(group);

        groupNeo4jRepository.addUserToGroup(targetUserId, groupId);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void rejectJoinRequest(User currentUser, String groupId, String targetUserId) throws Exception {
        GroupMemberId currentUserId = new GroupMemberId(groupId, currentUser.getId());
        GroupMember currentUserMember = groupMemberRepository.findById(currentUserId)
                .orElseThrow(() -> new Exception("Bạn không phải là thành viên của nhóm này"));

        assertGroupActive(currentUserMember.getGroup());

        if (currentUserMember.getRole() != MemberRole.LEADER) {
            throw new ForbiddenAccessException("Chỉ trưởng nhóm mới có quyền từ chối thành viên");
        }

        GroupMemberId targetId = new GroupMemberId(groupId, targetUserId);
        GroupMember targetMember = groupMemberRepository.findById(targetId)
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy yêu cầu tham gia của người dùng này"));

        if (targetMember.getStatus() == MemberStatus.APPROVED) {
            throw new Exception("Người dùng này đã là thành viên của nhóm");
        }

        targetMember.setStatus(MemberStatus.REJECTED);
        groupMemberRepository.save(targetMember);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void kickMember(User currentUser, String groupId, String targetUserId) throws Exception {
        GroupMemberId currentUserId = new GroupMemberId(groupId, currentUser.getId());
        GroupMember currentUserMember = groupMemberRepository.findById(currentUserId)
                .orElseThrow(() -> new Exception("Bạn không phải là thành viên của nhóm này"));

        assertGroupActive(currentUserMember.getGroup());

        if (currentUserMember.getRole() != MemberRole.LEADER) {
            throw new ForbiddenAccessException("Chỉ trưởng nhóm mới có quyền đuổi thành viên");
        }

        if (currentUser.getId().equals(targetUserId)) {
            throw new Exception("Bạn không thể tự đuổi chính mình");
        }

        GroupMemberId targetId = new GroupMemberId(groupId, targetUserId);
        GroupMember targetMember = groupMemberRepository.findById(targetId)
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy thành viên này trong nhóm"));

        if (targetMember.getStatus() != MemberStatus.APPROVED) {
            throw new Exception("Người dùng này chưa phải là thành viên chính thức của nhóm");
        }

        groupMemberRepository.delete(targetMember);

        Group group = currentUserMember.getGroup();
        group.setMemberCount(group.getMemberCount() - 1);
        groupRepository.save(group);

        groupNeo4jRepository.removeUserFromGroup(targetUserId, groupId);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void leaveGroup(User currentUser, String groupId) throws Exception {
        GroupMemberId currentUserId = new GroupMemberId(groupId, currentUser.getId());
        GroupMember currentUserMember = groupMemberRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("Bạn chưa tham gia nhóm này"));

        if (currentUserMember.getRole() == MemberRole.LEADER) {
            throw new ForbiddenAccessException("Trưởng nhóm không thể tự rời nhóm. Hãy nhường quyền cho người khác trước.");
        }

        groupMemberRepository.delete(currentUserMember);

        Group group = currentUserMember.getGroup();
        group.setMemberCount(group.getMemberCount() - 1);
        groupRepository.save(group);

        groupNeo4jRepository.removeUserFromGroup(currentUser.getId(), groupId);
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

        return groupMemberRepository.findByGroup_IdInAndRoleAndStatus(groupIds, MemberRole.LEADER, MemberStatus.APPROVED)
                .stream()
                .collect(Collectors.toMap(
                        member -> member.getId().getGroupId(),
                        member -> member,
                        (existing, replacement) -> existing
                ));
    }

    private void assertGroupActive(Group group) {
        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw new ForbiddenAccessException("Nhóm này đã bị lưu trữ và không còn cho phép thao tác mới");
        }
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public GroupResponse adminLockGroup(String groupId) throws Exception {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy nhóm"));

        group.setStatus(GroupStatus.ARCHIVED);
        group = groupRepository.save(group);
        return GroupResponse.fromGroup(group, null, findLeaderMember(groupId));
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public GroupResponse adminUnlockGroup(String groupId) throws Exception {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy nhóm"));

        group.setStatus(GroupStatus.ACTIVE);
        group = groupRepository.save(group);
        return GroupResponse.fromGroup(group, null, findLeaderMember(groupId));
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public GroupResponse updateGroupName(User currentUser, String groupId, String name) throws Exception {
        GroupMemberId currentUserId = new GroupMemberId(groupId, currentUser.getId());
        GroupMember currentUserMember = groupMemberRepository.findById(currentUserId)
                .orElseThrow(() -> new ForbiddenAccessException("Bạn không phải là thành viên của nhóm này"));

        assertGroupActive(currentUserMember.getGroup());

        if (currentUserMember.getRole() != MemberRole.LEADER) {
            throw new ForbiddenAccessException("Chỉ trưởng nhóm mới có quyền thay đổi tên nhóm");
        }

        Group group = currentUserMember.getGroup();
        group.setName(name);
        groupRepository.save(group);
        
        try {
            groupNeo4jRepository.updateGroupName(groupId, name);
        } catch (Exception e) {
            throw new Exception("Lỗi khi cập nhật tên nhóm trên Neo4j: " + e.getMessage());
        }

        return GroupResponse.fromGroup(group, currentUserMember);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public GroupResponse updateGroupDescription(User currentUser, String groupId, String description) throws Exception {
        GroupMemberId currentUserId = new GroupMemberId(groupId, currentUser.getId());
        GroupMember currentUserMember = groupMemberRepository.findById(currentUserId)
                .orElseThrow(() -> new ForbiddenAccessException("Bạn không phải là thành viên của nhóm này"));

        assertGroupActive(currentUserMember.getGroup());

        if (currentUserMember.getRole() != MemberRole.LEADER) {
            throw new ForbiddenAccessException("Chỉ trưởng nhóm mới có quyền thay đổi mô tả nhóm");
        }

        Group group = currentUserMember.getGroup();
        group.setDescription(description);
        groupRepository.save(group);

        return GroupResponse.fromGroup(group, currentUserMember);
    }
}
