package com.example.campushub.controllers.users;

import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.campushub.dtos.users.ProfileSetUpDTO;
import com.example.campushub.dtos.users.UpdateInformationDTO;
import com.example.campushub.dtos.users.UpdatePasswordDTO;
import com.example.campushub.models.jpa.User;
import com.example.campushub.responses.ApiResponse;
import com.example.campushub.responses.profiles.InformationResponse;
import com.example.campushub.services.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users/profiles")
@RequiredArgsConstructor
public class UserProfileController {
    private final UserService userService;

    @PostMapping("/setup")
    public ResponseEntity<?> setupProfile(@AuthenticationPrincipal User currentUser, @RequestBody @Valid ProfileSetUpDTO dto) throws Exception {
        userService.setupUserProfile(currentUser, dto.getMajor(), dto.getHobbies());
        return ResponseEntity.ok().body(ApiResponse.ok("Profile set up successfully"));
    }

    @PutMapping("/information")
    public ResponseEntity<?> updateInformation(@AuthenticationPrincipal User currentUser, @RequestBody @Valid UpdateInformationDTO dto) throws Exception {
        userService.updateInformationUser(currentUser, dto);
        return ResponseEntity.ok().body(ApiResponse.ok("User information updated successfully"));
    }

    @PutMapping("/password")
    public ResponseEntity<?> changePassword(@AuthenticationPrincipal User currentUser, @RequestBody @Valid UpdatePasswordDTO dto) throws Exception {
        if(!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("New password and confirm password do not match"));
        }
        userService.updatePasswordUser(currentUser, dto.getCurrentPassword(), dto.getNewPassword());
        return ResponseEntity.ok().body(ApiResponse.ok("Change password successfully"));
    }

    @PutMapping("/avatar")
    public ResponseEntity<?> updateAvatar(@AuthenticationPrincipal User currentUser, @RequestParam("avatarFile") MultipartFile avatarFile) throws Exception {
        userService.updateAvatarUser(currentUser, avatarFile);
        return ResponseEntity.ok().body(ApiResponse.ok("Cập nhật ảnh đại diện thành công"));
    }

    @PutMapping("/bio")
    public ResponseEntity<?> updateBio(@AuthenticationPrincipal User currentUser, @RequestBody String bio) {
        userService.updateBioUser(currentUser, bio);
        return ResponseEntity.ok().body(ApiResponse.ok("Cập nhật giới thiệu thành công"));
    }

    @PutMapping("/topic")
    public ResponseEntity<?> updateTopic(@AuthenticationPrincipal User currentUser, @RequestBody Set<String> topic) {
        userService.updateTopicUser(currentUser, topic);
        return ResponseEntity.ok().body(ApiResponse.ok("Cập nhật chủ đề quan tâm thành công"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal User currentUser) {
        Set<String> topics = userService.getUserTopics(currentUser); 
        return ResponseEntity.ok().body(ApiResponse.ok(InformationResponse.builder()
                .fullname(currentUser.getFullName())
                .avatarUrl(currentUser.getAvatarUrl())
                .dateOfBirth(currentUser.getDateOfBirth())
                .phone(currentUser.getPhone())
                .gender(currentUser.getGender() != null ? currentUser.getGender() : false)
                .email(currentUser.getEmail())
                .bio(currentUser.getBio())
                .department(currentUser.getDepartment())
                .topics(topics)
                .build()));
    }
}
