package com.example.campushub.responses;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FriendSuggestionPageResponse {
    private List<FollowResponse> items;
}
