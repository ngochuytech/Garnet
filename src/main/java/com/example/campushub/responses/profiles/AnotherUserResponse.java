package com.example.campushub.responses.profiles;

import java.time.LocalDateTime;
import java.util.List;

import com.example.campushub.responses.GroupResponse;
import com.example.campushub.responses.TopicResponse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AnotherUserResponse {
    private String id;
    private String fullname;
    private String avatarUrl;
    private String bio;
    private String department;
    private boolean isFollowing;
    private Long followersCount;
    private Long followingCount;
    private List<TopicResponse> topics;
    private List<GroupResponse> groups;
    private LocalDateTime createdAt;
}
