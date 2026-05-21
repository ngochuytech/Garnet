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
        private Integer rank;
        @JsonProperty("user_id")
        private String userId;
        @JsonProperty("backend_user_id")
        private String backendUserId;
        @JsonProperty("han_user_id")
        private Integer hanUserId;
        private Double score;
        @JsonProperty("match_percent")
        private Double matchPercent;
        private Profile profile;
        @JsonProperty("common_interests")
        private List<String> commonInterests;
        private Features features;
        @JsonProperty("is_val_friend")
        private Boolean isValFriend;
        @JsonProperty("is_test_friend")
        private Boolean isTestFriend;
        @JsonProperty("is_known_friend")
        private Boolean isKnownFriend;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Profile {
        @JsonProperty("major_id")
        private Integer majorId;
        @JsonProperty("major_name")
        private String majorName;
        @JsonProperty("group_id")
        private Integer groupId;
        @JsonProperty("group_name")
        private String groupName;
        private List<Interest> interests;
        @JsonProperty("interest_text")
        private String interestText;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Interest {
        @JsonProperty("interest_id")
        private Integer interestId;
        @JsonProperty("interest_name")
        private String interestName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Features {
        @JsonProperty("same_major")
        private Boolean sameMajor;
        @JsonProperty("same_group")
        private Boolean sameGroup;
        @JsonProperty("same_interest")
        private Boolean sameInterest;
    }
}
