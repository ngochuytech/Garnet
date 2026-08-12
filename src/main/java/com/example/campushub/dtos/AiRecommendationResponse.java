package com.example.campushub.dtos;

import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiRecommendationResponse {
    String sourceUserId;
    String modelVersion;
    String rankingTier;
    LocalDateTime generatedAt;
    String nextCursor;
    List<Candidates> candidates;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Candidates {
        String candidateUserId;

        Integer rank;
        String reasonCode;
        List<String> channels;
    }
}
