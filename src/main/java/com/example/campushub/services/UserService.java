package com.example.campushub.services;

import java.security.InvalidParameterException;
import java.util.List;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.campushub.dtos.users.UpdateInformationDTO;
import com.example.campushub.exceptions.InvalidParamException;
import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.UserRepository;
import com.example.campushub.repositories.neo4j.TagNeo4jRepository;
import com.example.campushub.repositories.neo4j.UserNeo4jRepository;
import com.example.campushub.responses.TopicResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final UserNeo4jRepository userNeo4jRepository;
    private final TagNeo4jRepository tagNeo4jRepository;
    private final FileUploadService fileUploadService;

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
        return tagNeo4jRepository.getTopicUserCounts(user.getId());
    }
}
