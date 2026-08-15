package com.example.campushub.dtos.recommendation;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RecommendationFeedResponse(
        @JsonProperty("user_id") String userId,
        @JsonProperty("encoder_version") String encoderVersion,
        @JsonProperty("index_size") Integer indexSize,
        List<RecommendationItem> items,
        @JsonProperty("next_cursor") String nextCursor) {
}
