package com.example.campushub.services;

import java.security.InvalidParameterException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.campushub.dtos.users.UpdateInformationDTO;
import com.example.campushub.enums.UserRole;
import com.example.campushub.enums.UserStatus;
import com.example.campushub.exceptions.DataNotFoundException;
import com.example.campushub.exceptions.ForbiddenAccessException;
import com.example.campushub.exceptions.InvalidParamException;
import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.UserRepository;
import com.example.campushub.repositories.neo4j.MajorNeo4jRepository;
import com.example.campushub.repositories.neo4j.InterestNeo4jRepository;
import com.example.campushub.repositories.neo4j.UserNeo4jRepository;
import com.example.campushub.responses.TopicResponse;
import com.example.campushub.responses.admin.AdminUserResponse;

import lombok.RequiredArgsConstructor;
import net.datafaker.Faker;

@Service
@RequiredArgsConstructor
public class UserService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final UserNeo4jRepository userNeo4jRepository;
    private final MajorNeo4jRepository majorNeo4jRepository;
    private final InterestNeo4jRepository interestNeo4jRepository;
    private final FileUploadService fileUploadService;
    private final Faker faker;

    private UserStatus parseAndValidateUserStatus(String status){
        if(status==null || status.isBlank())
            return null;
        try {
            UserStatus userStatus = UserStatus.valueOf(status);
            return userStatus;
        } catch (Exception e) {
            throw new InvalidParamException("Tham số user status không hợp lệ: " + status);
        }
    }

    public User getUserFromEmail(String email) throws Exception {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new Exception("User not found"));
    }

    public User getUserById(String id) throws Exception {
        return userRepository.findById(id)
                .orElseThrow(() -> new Exception("User not found"));
    }

    public void updateInformationUser(User user, UpdateInformationDTO dto) throws Exception {
        if (dto.getFullname() == null || dto.getFullname().isEmpty()) {
            throw new InvalidParameterException("Full name is required");
        }
        user.setFullName(dto.getFullname());
        user.setDateOfBirth(dto.getDateOfBirth());
        user.setPhone(dto.getPhone());
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
            throw new InvalidParameterException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public void updateBioUser(User user, String bio) {
        int wordCount = bio.trim().split("\\s+").length;
        if (wordCount > 1000) {
            throw new InvalidParamException("Bio cannot exceed 1000 words. Current: " + wordCount + " words");
        }

        user.setBio(bio);
        userRepository.save(user);
    }

    public void setupUserProfile(User user, String major, Set<String> hobbies) {
        user.setDepartment(major);
        user.setInterests(hobbies);
        user = userRepository.save(user);

        try {
            if (major != null && !major.trim().isEmpty()) {
                userNeo4jRepository.updateUserMajor(user.getId(), major);
            }
            if (hobbies != null && !hobbies.isEmpty()) {
                userNeo4jRepository.updateUserTags(user.getId(), hobbies);
            }
        } catch (Exception e) {
            // Rollback thủ công trên JPA nếu thao tác trên Neo4j thất bại
            user.setDepartment(null);
            user.setInterests(null);
            userRepository.save(user);
            throw new RuntimeException("Cập nhật hồ sơ thất bại tại Neo4j", e);
        }
    }

    public void updateAvatarUser(User user, MultipartFile avatarFile) throws Exception {
        if (avatarFile == null || avatarFile.isEmpty()) {
            throw new IllegalArgumentException("File ảnh không được để trống");
        }
        // Validate file type and size
        String contentType = avatarFile.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new InvalidParameterException("File phải là định dạng hình ảnh");
        }
        if (avatarFile.getSize() > 5 * 1024 * 1024) { // 5MB limit
            throw new InvalidParameterException("Kích thước ảnh tối đa 5MB.");
        }

        // Upload to Cloudinary and get the URL
        String avatarUrl = fileUploadService.uploadFile(avatarFile, "avatars");

        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);
    }

    @Transactional("neo4jTransactionManager")
    public void updateTopicUser(User user, Set<String> topic) {
        Set<String> oldTopics = user.getInterests();
        boolean isNull = topic == null || topic.isEmpty();
        
        Set<String> newTopics = isNull ? Set.of() : topic;
        user.setInterests(newTopics);
        user = userRepository.save(user);

        try {
            userNeo4jRepository.removeOldTopics(user.getId(), newTopics);
            if (!newTopics.isEmpty()) {
                userNeo4jRepository.addNewTopics(user.getId(), newTopics);
            }
        } catch (Exception e) {
            // Rollback thủ công trên JPA nếu thao tác trên Neo4j thất bại
            user.setInterests(oldTopics);
            userRepository.save(user);
            throw new RuntimeException("Cập nhật Topic thất bại tại Neo4j", e);
        }
    }

    public List<TopicResponse> getUserTopics(User user) {
        return interestNeo4jRepository.getTopicUserCounts(user.getId());
    }

    // --- ADMIN ---
    public Page<AdminUserResponse> getUsers(String query, String status, Pageable pageable) throws Exception{
        UserStatus userStatus = parseAndValidateUserStatus(status);
        if(query!=null && query.trim().isEmpty() )
            query = null;
        return userRepository.findByQueryAndOptionalStatus(query, userStatus, pageable)
            .map(AdminUserResponse::fromEntity);
    }

    public void banUser(User currentUser, String userId) throws Exception{
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DataNotFoundException("Người dùng không tồn tại"));

        if (user.getRole() == null || user.getRole().equals(UserRole.ADMIN)) {
            throw new ForbiddenAccessException("Không thể ban tài khoản admin");
        }

        user.setStatus(UserStatus.BANNED);
        userRepository.save(user);
    }

    public void unbanUser(User currentUser, String userId) throws Exception{
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DataNotFoundException("Người dùng không tồn tại"));

        if (user.getRole() == null || user.getRole().equals(UserRole.ADMIN)) {
            throw new ForbiddenAccessException("Không thể thay đổi trạng thái tài khoản admin");
        }

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }
    
    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int seedUser(int count){
        List<String> majors = majorNeo4jRepository.findAllMajorNames();
        List<String> tags = interestNeo4jRepository.findLeafTagsToList();
        List<String> mutableList = new ArrayList<>(tags);
        int successCount = 0;
        for (int i = 0; i < count; i++) {
            try {
                String fullName = faker.name().fullName();
                String email = faker.internet().emailAddress();
                String password = "password123"; // Mật khẩu mặc định cho người dùng giả
                boolean isGenderMale = faker.bool().bool();
                LocalDate dateOfBirth = faker.date().birthdayLocalDate(19, 26);
                String randomSeed = faker.internet().uuid();
                String avatarUrl = "https://api.dicebear.com/9.x/adventurer/svg?seed=" + randomSeed;

                String randomDept = faker.options().nextElement(majors);
                Collections.shuffle(mutableList);
                List<String> randomPicksTag = mutableList.subList(3, 7);

                User user = new User();
                user.setFullName(fullName);
                user.setEmail(email);
                user.setPassword(passwordEncoder.encode(password));
                user.setGender(isGenderMale);
                user.setDateOfBirth(dateOfBirth);
                user.setAvatarUrl(avatarUrl);
                user.setDepartment(randomDept);
                user.setInterests(Set.copyOf(randomPicksTag));
                user.setStatus(UserStatus.ACTIVE);

                user.setCreatedAt(LocalDateTime.now().minusMonths(1));
                userRepository.save(user);
                try {
                    userNeo4jRepository.updateUserMajor(user.getId(), randomDept);
                    userNeo4jRepository.updateUserTags(user.getId(), Set.copyOf(randomPicksTag));
                } catch (Exception e) {
                    throw new RuntimeException("Tạo người dùng thất bại tại Neo4j", e);
                }
                successCount++;
            } catch (Exception e) {
                // Bỏ qua lỗi và tiếp tục tạo người dùng tiếp theo
            }
        }
        return successCount;
    }


}
