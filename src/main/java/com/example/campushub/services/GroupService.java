package com.example.campushub.services;

import com.example.campushub.dtos.users.CreateGroupDTO;
import com.example.campushub.enums.GroupStatus;
import com.example.campushub.enums.MemberRole;
import com.example.campushub.enums.MemberStatus;
import com.example.campushub.exceptions.DataNotFoundException;
import com.example.campushub.exceptions.ForbiddenAccessException;
import com.example.campushub.models.jpa.Group;
import com.example.campushub.models.jpa.GroupMember;
import com.example.campushub.models.jpa.GroupMemberId;
import com.example.campushub.models.jpa.User;
import com.example.campushub.models.neo4j.GroupNode;
import com.example.campushub.repositories.jpa.GroupMemberRepository;
import com.example.campushub.repositories.jpa.GroupRepository;
import com.example.campushub.repositories.neo4j.GroupNeo4jRepository;
import com.example.campushub.responses.GroupResponse;
import lombok.RequiredArgsConstructor;
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
    private final GroupNeo4jRepository groupNeo4jRepository;
    private final FileUploadService fileUploadService;

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public GroupResponse createGroup(User user, CreateGroupDTO dto) {
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

        GroupMember adminMember = GroupMember.builder()
                .id(new GroupMemberId(group.getId(), user.getId()))
                .group(group)
                .user(user)
                .role(MemberRole.LEADER)
                .status(MemberStatus.APPROVED)
                .joinedAt(LocalDateTime.now())
                .build();

        groupMemberRepository.save(adminMember);
        groupNeo4jRepository.addUserToGroup(user.getId(), group.getId());

        return GroupResponse.fromGroup(group, adminMember);
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

        return groupRepository.findAll().stream()
                .map(group -> GroupResponse.fromGroup(group, currentUserMembers.get(group.getId())))
                .collect(Collectors.toList());
    }

    public GroupResponse getGroupById(User currentUser, String groupId) throws Exception {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy nhóm"));
        GroupMember currentUserMember = findCurrentUserMember(currentUser, groupId);

        return GroupResponse.fromGroup(group, currentUserMember);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public GroupResponse updateGroupAvatar(User currentUser, String groupId, MultipartFile file) throws Exception {
        GroupMemberId currentUserId = new GroupMemberId(groupId, currentUser.getId());
        GroupMember currentUserMember = groupMemberRepository.findById(currentUserId)
                .orElseThrow(() -> new Exception("Bạn không phải là thành viên của nhóm này"));

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
                .orElseThrow(() -> new Exception("Bạn chưa tham gia nhóm này"));

        if (currentUserMember.getRole() == MemberRole.LEADER) {
            throw new Exception("Trưởng nhóm không thể tự rời nhóm. Hãy nhường quyền cho người khác trước.");
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

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public GroupResponse updateGroupName(User currentUser, String groupId, String name) throws Exception {
        GroupMemberId currentUserId = new GroupMemberId(groupId, currentUser.getId());
        GroupMember currentUserMember = groupMemberRepository.findById(currentUserId)
                .orElseThrow(() -> new ForbiddenAccessException("Bạn không phải là thành viên của nhóm này"));

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
}
