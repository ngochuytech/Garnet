package com.example.campushub.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.campushub.dtos.AiRecommendationResponse;

class FollowServiceTest {
    private final FollowService followService = new FollowService(null, null, null, null);

    @Test
    void includesInterestsMajorAndGroupAsSeparateReasons() {
        AiRecommendationResponse.Recommendation recommendation = recommendation(
                List.of("Internet", "Lập trình"),
                true,
                true);

        assertEquals(
                List.of("Internet", "Lập trình", "Cùng ngành", "Cùng nhóm"),
                followService.buildSuggestionReasons(recommendation));
    }

    @Test
    void includesMajorAndGroupWhenThereAreNoCommonInterests() {
        AiRecommendationResponse.Recommendation recommendation = recommendation(
                List.of(),
                true,
                true);

        assertEquals(
                List.of("Cùng ngành", "Cùng nhóm"),
                followService.buildSuggestionReasons(recommendation));
    }

    @Test
    void includesGroupWhenItIsTheOnlyMatchingFeature() {
        AiRecommendationResponse.Recommendation recommendation = recommendation(
                List.of(),
                false,
                true);

        assertEquals(List.of("Cùng nhóm"), followService.buildSuggestionReasons(recommendation));
    }

    @Test
    void reasonsAreEmptyWhenFastApiProvidesNoMatchingFeature() {
        AiRecommendationResponse.Recommendation recommendation = recommendation(
                List.of(),
                false,
                false);

        assertEquals(List.of(), followService.buildSuggestionReasons(recommendation));
    }

    @Test
    void mergeSuggestionIdsKeepsPrimaryIdsFirstAndFillsFromFallback() {
        List<String> mergedIds = followService.mergeSuggestionIds(
                List.of("u1", "u2", "u3"),
                List.of("u2", "u4", "u5", "u6"));

        assertEquals(List.of("u1", "u2", "u3", "u4", "u5"), mergedIds);
    }

    @Test
    void mergeSuggestionIdsUsesFallbackWhenPrimaryIdsAreMissing() {
        List<String> mergedIds = followService.mergeSuggestionIds(
                null,
                List.of("u4", "", "u5", "u5", "u6"));

        assertEquals(List.of("u4", "u5", "u6"), mergedIds);
    }

    private AiRecommendationResponse.Recommendation recommendation(
            List<String> commonInterests,
            boolean sameMajor,
            boolean sameGroup) {
        AiRecommendationResponse.Features features = new AiRecommendationResponse.Features();
        features.setSameMajor(sameMajor);
        features.setSameGroup(sameGroup);

        AiRecommendationResponse.Recommendation recommendation =
                new AiRecommendationResponse.Recommendation();
        recommendation.setCommonInterests(commonInterests);
        recommendation.setFeatures(features);
        return recommendation;
    }
}
