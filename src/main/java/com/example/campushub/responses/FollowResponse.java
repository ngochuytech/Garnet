package com.example.campushub.responses;

import java.util.List;

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
public class FollowResponse {
    private String id;
    private String fullName;
    private String avatarUrl;
    private String department;
    private List<String> commonInterests;
}
