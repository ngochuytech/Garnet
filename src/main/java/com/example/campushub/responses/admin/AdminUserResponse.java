package com.example.campushub.responses.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.example.campushub.models.jpa.User;

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
public class AdminUserResponse {
    private String id;
    private String fullName;
    private String avatarUrl;
    private String department;
    private LocalDate dateOfBirth;
    private String email;
    private String status;
    private String role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AdminUserResponse fromEntity(User user){
        return AdminUserResponse.builder()
        .id(user.getId())
        .fullName(user.getFullName())
        .avatarUrl(user.getAvatarUrl())
        .department(user.getDepartment())
        .dateOfBirth(user.getDateOfBirth())
        .email(user.getEmail())
        .status(user.getStatus().name())
        .role(user.getRole().name())
        .createdAt(user.getCreatedAt())
        .updatedAt(user.getUpdatedAt())
        .build();
    }
}
