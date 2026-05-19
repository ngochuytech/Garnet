package com.example.campushub.dtos;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendationResponse {
    @JsonProperty("target_user_id")
    private String targetUserId;
    private String status;
    private List<Recommendation> recommendations;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Recommendation {
        @JsonProperty("user_id")
        private String userId;
        @JsonProperty("match_score")
        private Double matchScore;
        @JsonProperty("match_percent")
        private Double matchPercent;
        private Info info;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Info {
        private String major;
        private String interests;
    }
}
