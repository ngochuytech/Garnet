package com.example.campushub.responses;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> reason;
}
