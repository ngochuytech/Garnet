package com.example.campushub.responses.profiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.example.campushub.responses.TopicResponse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InformationResponse {
    private String fullname;
    private String avatarUrl;
    private LocalDate dateOfBirth;
    private boolean gender;
    private String email;
    private String bio;
    private String department;
    private Long followersCount;
    private Long followingCount;
    private List<TopicResponse> topics;
    private LocalDateTime createdAt;

}
