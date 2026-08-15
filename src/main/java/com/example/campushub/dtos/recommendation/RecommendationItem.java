package com.example.campushub.dtos.recommendation;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RecommendationItem(
        @JsonProperty("post_id") String postId,
        Double score,
        @JsonProperty("content_score") Double contentScore,
        @JsonProperty("negative_penalty") Double negativePenalty,
        @JsonProperty("seen_penalty") Double seenPenalty,
        List<String> sources) {
}
