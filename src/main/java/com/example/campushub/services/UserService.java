package com.example.campushub.services;


import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.campushub.dtos.record.profiles.UserProfileUpdatedPayload;
import com.example.campushub.dtos.record.users.UserStatusChangedPayload;
import com.example.campushub.dtos.users.UpdateInformationDTO;
import com.example.campushub.enums.GroupStatus;
import com.example.campushub.enums.MemberStatus;
import com.example.campushub.enums.Neo4jEventType;
import com.example.campushub.enums.UserRole;
import com.example.campushub.enums.UserStatus;
import com.example.campushub.exceptions.BadRequestException;
import com.example.campushub.exceptions.ForbiddenException;
import com.example.campushub.exceptions.ResourceNotFoundException;
import com.example.campushub.models.jpa.GroupMember;
import com.example.campushub.models.jpa.Neo4jSyncEvent;
import com.example.campushub.models.jpa.User;
import com.example.campushub.models.jpa.UserInterest;
import com.example.campushub.models.jpa.UserInterestId;
import com.example.campushub.repositories.jpa.GroupMemberRepository;
import com.example.campushub.repositories.jpa.Neo4jSyncEventRepository;
import com.example.campushub.repositories.jpa.UserInterestRepository;
import com.example.campushub.repositories.jpa.UserRepository;
import com.example.campushub.repositories.neo4j.InterestNeo4jRepository;
import com.example.campushub.responses.GroupResponse;
import com.example.campushub.responses.TopicResponse;
import com.example.campushub.responses.admin.AdminUserResponse;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final InterestNeo4jRepository interestNeo4jRepository;
    private final Neo4jSyncEventRepository neo4jSyncEventRepository;
    private final UserInterestRepository userInterestRepository;
    private final ObjectMapper objectMapper;

    private UserStatus parseAndValidateUserStatus(String status) {
        if (status == null || status.isBlank())
            return null;
        try {
            UserStatus userStatus = UserStatus.valueOf(status);
            return userStatus;
        } catch (Exception e) {
            throw new BadRequestException("Tham số user status không hợp lệ: " + status);
        }
    }

    private String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert object to JSON", e);
        }
    }

    public User getUserFromEmail(String email) throws ResourceNotFoundException{
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));
    }

    public User getUserById(String id) throws ResourceNotFoundException {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));
    }

    public void updateInformationUser(User user, UpdateInformationDTO dto) throws Exception {
        if (dto.getFullname() == null || dto.getFullname().isEmpty()) {
            throw new BadRequestException("Full name is required");
        }
        user.setFullName(dto.getFullname());
        user.setDateOfBirth(dto.getDateOfBirth());
        if (dto.getGender().equals("Nam"))
            user.setGender(true);
        else if (dto.getGender().equals("Nữ"))
            user.setGender(false);
        else
            user.setGender(null);
        userRepository.save(user);
    }

    public void updatePasswordUser(User user, String currentPassword, String newPassword) throws Exception {
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public void updateBioUser(User user, String bio) {
        int wordCount = bio.trim().split("\\s+").length;
        if (wordCount > 1000) {
            throw new BadRequestException("Bio cannot exceed 1000 words. Current: " + wordCount + " words");
        }

        user.setBio(bio);
        userRepository.save(user);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void setupUserProfile(User user, String major, Set<String> hobbies) {
        user.setDepartment(major);
        userRepository.save(user);

        userInterestRepository.deleteByIdUserId(user.getId());

        Set<String> newHobbies = (hobbies == null || hobbies.isEmpty()) ? Set.of() : hobbies;
        List<UserInterest> interests = newHobbies.stream()
                .map(name -> UserInterest.builder()
                        .id(new UserInterestId(user.getId(), name))
                        .user(user)
                        .build())
                .toList();

        userInterestRepository.saveAll(interests);

        UserProfileUpdatedPayload payload = new UserProfileUpdatedPayload(user.getId(), major, newHobbies, user.getStatus());
        neo4jSyncEventRepository.save(Neo4jSyncEvent.pending(
                Neo4jEventType.USER_PROFILE_UPDATED,
                user.getId(),
                toJson(payload)));
    }

    public void updateAvatarUser(User user, String avatarUrl) throws Exception {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            throw new IllegalArgumentException("Đường dẫn ảnh đại diện không được để trống");
        }

        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);
    }

    @Transactional("transactionManager")
    public void updateTopicUser(User user, Set<String> topic) {
        boolean isNull = topic == null || topic.isEmpty();

        Set<String> newTopics = isNull ? Set.of() : topic;

        userInterestRepository.deleteByIdUserId(user.getId());

        List<UserInterest> interests = newTopics.stream()
                .map(name -> UserInterest.builder()
                        .id(new UserInterestId(user.getId(), name))
                        .user(user)
                        .build())
                .toList();

        userInterestRepository.saveAll(interests);

        UserProfileUpdatedPayload payload = new UserProfileUpdatedPayload(user.getId(), user.getDepartment(),
                newTopics, user.getStatus());

        neo4jSyncEventRepository.save(Neo4jSyncEvent.pending(
                Neo4jEventType.USER_PROFILE_UPDATED,
                user.getId(),
                toJson(payload)));
    }

    public List<TopicResponse> getUserTopics(User user) {
        return interestNeo4jRepository.getTopicUserCounts(user.getId());
    }

    public List<GroupResponse> getJoinedGroups(User user) {
        if (user == null || user.getId() == null) {
            return List.of();
        }

        List<GroupMember> memberships = groupMemberRepository
                .findByUser_IdAndStatus(user.getId(), MemberStatus.APPROVED)
                .stream()
                .filter(member -> member.getGroup() != null)
                .filter(member -> member.getGroup().getStatus() == GroupStatus.ACTIVE)
                .toList();
        if (memberships.isEmpty()) {
            return List.of();
        }

        return memberships.stream()
                .map(member -> GroupResponse.fromGroup(member.getGroup(), member))
                .toList();
    }

    // --- ADMIN ---
    public Page<AdminUserResponse> getUsers(String query, String status, Pageable pageable) throws Exception {
        UserStatus userStatus = parseAndValidateUserStatus(status);
        if (query != null && query.trim().isEmpty())
            query = null;
        return userRepository.findByQueryAndOptionalStatus(query, userStatus, pageable)
                .map(AdminUserResponse::fromEntity);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void banUser(User currentUser, String userId) throws Exception {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        if (user.getRole() == null || user.getRole().equals(UserRole.ADMIN)) {
            throw new ForbiddenException("Không thể ban tài khoản admin");
        }

        user.setStatus(UserStatus.BANNED);
        userRepository.save(user);
        queueUserStatusChangedEvent(user);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void unbanUser(User currentUser, String userId) throws Exception {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        if (user.getRole() == null || user.getRole().equals(UserRole.ADMIN)) {
            throw new ForbiddenException("Không thể thay đổi trạng thái tài khoản admin");
        }

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        queueUserStatusChangedEvent(user);
    }

    private void queueUserStatusChangedEvent(User user) {
        UserStatusChangedPayload payload = new UserStatusChangedPayload(user.getId(), user.getStatus());
        neo4jSyncEventRepository.save(Neo4jSyncEvent.pending(
                Neo4jEventType.USER_STATUS_CHANGED,
                user.getId(),
                toJson(payload)));
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy user: " + email));
    }

}
