package com.example.campushub.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.example.campushub.dtos.AiRecommendationResponse;
import com.example.campushub.enums.NotificationType;
import com.example.campushub.events.NotificationEvent;
import com.example.campushub.exceptions.BadRequestException;
import com.example.campushub.exceptions.ResourceNotFoundException;
import com.example.campushub.models.jpa.Notification;
import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.NotificationRepository;
import com.example.campushub.repositories.jpa.UserRepository;
import com.example.campushub.repositories.neo4j.UserNeo4jRepository;
import com.example.campushub.responses.FollowResponse;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock
    private UserNeo4jRepository userNeo4jRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FollowService followService;

    @Test
    void followUserCreatesRelationshipAndPublishesNewFollowerEvent() throws Exception {
        User currentUser = user("current-user", "Current User");
        User targetUser = user("target-user", "Target User");
        when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));
        when(userRepository.findById(currentUser.getId())).thenReturn(Optional.of(currentUser));
        when(userNeo4jRepository.followUser(currentUser.getId(), targetUser.getId())).thenReturn(true);

        followService.followUser(currentUser.getId(), targetUser.getId());

        verify(userNeo4jRepository).followUser(currentUser.getId(), targetUser.getId());
        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        NotificationEvent event = eventCaptor.getValue();
        assertEquals(currentUser.getId(), event.getActorId());
        assertEquals(targetUser.getId(), event.getRecipientId());
        assertEquals(NotificationType.NEW_FOLLOWER, event.getType());
    }

    @Test
    void followUserRejectsSelfFollow() {
        assertThrows(BadRequestException.class,
                () -> followService.followUser("current-user", "current-user"));
    }

    @Test
    void followUserRejectsMissingTarget() {
        when(userRepository.findById("missing-user")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> followService.followUser("current-user", "missing-user"));
    }

    @Test
    void followUserRejectsBannedTarget() {
        User bannedUser = user("banned-user", "Banned User");
        bannedUser.setStatus(com.example.campushub.enums.UserStatus.BANNED);
        when(userRepository.findById("banned-user")).thenReturn(Optional.of(bannedUser));

        assertThrows(BadRequestException.class,
                () -> followService.followUser("current-user", "banned-user"));

        verify(userNeo4jRepository, never()).followUser("current-user", "banned-user");
    }

    @Test
    void followUserRejectsInactiveTarget() {
        User inactiveUser = user("inactive-user", "Inactive User");
        inactiveUser.setStatus(com.example.campushub.enums.UserStatus.INACTIVE);
        when(userRepository.findById("inactive-user")).thenReturn(Optional.of(inactiveUser));

        assertThrows(BadRequestException.class,
                () -> followService.followUser("current-user", "inactive-user"));

        verify(userNeo4jRepository, never()).followUser("current-user", "inactive-user");
    }

    @Test
    void unfollowUserDeletesRelationshipAndExistingNotification() throws Exception {
        Notification notification = Notification.builder().id("notification-id").build();
        when(userRepository.existsById("target-user")).thenReturn(true);
        when(notificationRepository.findFirstByRecipientIdAndActorIdAndType(
                "target-user", "current-user", NotificationType.NEW_FOLLOWER))
                .thenReturn(Optional.of(notification));

        followService.unfollowUser("current-user", "target-user");

        verify(userNeo4jRepository).unfollowUser("current-user", "target-user");
        verify(notificationRepository).delete(notification);
    }

    @Test
    void recommendationMapsSharedInterestIntoExplainableReason() {
        AiRecommendationResponse.Recommendation recommendation = recommendation(
                "candidate", 0.8, List.of("Chess"), false, true, false);

        assertEquals(List.of("Chess"), followService.buildSuggestionReasons(recommendation));
    }

    @Test
    void recommendationPassesFollowedUsersAsAiExclusions() {
        AiRecommendationResponse response = aiResponse(
                recommendation("candidate", 0.8, List.of("Chess"), false, false, true));
        when(userNeo4jRepository.findAllFollowingIds("source"))
                .thenReturn(List.of("already-followed"));
        when(userRepository.findAllById(List.of("candidate")))
                .thenReturn(List.of(user("candidate", "Candidate")));

        try (MockedConstruction<RestTemplate> restTemplates = Mockito.mockConstruction(
                RestTemplate.class,
                (mock, context) -> when(mock.getForObject(anyString(), eq(AiRecommendationResponse.class)))
                        .thenReturn(response))) {
            List<FollowResponse> suggestions = followService.getWhoToFollow("source");

            assertEquals(List.of("candidate"), suggestions.stream().map(FollowResponse::getId).toList());
            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
            verify(restTemplates.constructed().getFirst()).getForObject(urlCaptor.capture(), eq(AiRecommendationResponse.class));
            assertTrue(urlCaptor.getValue().contains("exclude_user_ids=already-followed"));
            assertTrue(urlCaptor.getValue().contains("top_k=20"));
        }
    }

    @Test
    void recommendationKeepsAiRankingOrderForBackendResponse() {
        AiRecommendationResponse response = aiResponse(
                recommendation("first", 0.9, List.of(), false, false, false),
                recommendation("second", 0.7, List.of(), false, false, false),
                recommendation("third", 0.2, List.of(), false, false, false));
        when(userNeo4jRepository.findAllFollowingIds("source")).thenReturn(List.of());
        when(userRepository.findAllById(List.of("first", "second", "third"))).thenReturn(List.of(
                user("first", "First"), user("second", "Second"), user("third", "Third")));

        try (MockedConstruction<RestTemplate> ignored = Mockito.mockConstruction(
                RestTemplate.class,
                (mock, context) -> when(mock.getForObject(anyString(), eq(AiRecommendationResponse.class)))
                        .thenReturn(response))) {
            List<FollowResponse> suggestions = followService.getWhoToFollow("source");

            assertEquals(List.of("first", "second", "third"),
                    suggestions.stream().map(FollowResponse::getId).toList());
        }
    }

    @Test
    void coldStartUsesNeo4jFallbackWhenAiIsUnavailable() {
        when(userNeo4jRepository.findAllFollowingIds("new-user")).thenReturn(List.of());
        when(userNeo4jRepository.getSuggestedUserByHooby("new-user", 20)).thenReturn(List.of("fallback-user"));
        when(userRepository.findAllById(List.of("fallback-user")))
                .thenReturn(List.of(user("fallback-user", "Fallback User")));

        try (MockedConstruction<RestTemplate> ignored = org.mockito.Mockito.mockConstruction(
                RestTemplate.class,
                (mock, context) -> when(mock.getForObject(anyString(), eq(AiRecommendationResponse.class)))
                        .thenThrow(new ResourceAccessException("FastAPI is unavailable")))) {
            List<FollowResponse> suggestions = followService.getWhoToFollow("new-user");

            assertEquals(List.of("fallback-user"), suggestions.stream().map(FollowResponse::getId).toList());
            verify(userNeo4jRepository).getSuggestedUserByHooby("new-user", 20);
        }
    }

    @Test
    void recommendationMustExcludeBannedCandidates() {
        User bannedUser = user("banned-user", "Banned User");
        bannedUser.setStatus(com.example.campushub.enums.UserStatus.BANNED);
        User inactiveUser = user("inactive-user", "Inactive User");
        inactiveUser.setStatus(com.example.campushub.enums.UserStatus.INACTIVE);
        User activeUser = user("active-user", "Active User");
        AiRecommendationResponse response = aiResponse(
                recommendation("banned-user", 0.9, List.of(), false, false, false),
                recommendation("inactive-user", 0.85, List.of(), false, false, false),
                recommendation("active-user", 0.8, List.of(), false, false, false));
        when(userNeo4jRepository.findAllFollowingIds("source")).thenReturn(List.of());
        when(userRepository.findAllById(List.of("banned-user", "inactive-user", "active-user")))
                .thenReturn(List.of(bannedUser, inactiveUser, activeUser));

        try (MockedConstruction<RestTemplate> ignored = org.mockito.Mockito.mockConstruction(
                RestTemplate.class,
                (mock, context) -> when(mock.getForObject(anyString(), eq(AiRecommendationResponse.class)))
                        .thenReturn(response))) {
            List<FollowResponse> suggestions = followService.getWhoToFollow("source");

            assertEquals(List.of("active-user"), suggestions.stream().map(FollowResponse::getId).toList());
        }
    }

    @Test
    void recommendationFillsFilteredSlotsWithActiveFallbackCandidates() {
        User bannedUser = user("banned-user", "Banned User");
        bannedUser.setStatus(com.example.campushub.enums.UserStatus.BANNED);
        User inactiveUser = user("inactive-user", "Inactive User");
        inactiveUser.setStatus(com.example.campushub.enums.UserStatus.INACTIVE);
        User aiCandidate = user("ai-candidate", "AI Candidate");
        List<String> primaryIds = List.of("banned-user", "inactive-user", "ai-candidate");
        List<String> fallbackIds = List.of("fallback-1", "fallback-2", "fallback-3", "fallback-4");
        List<String> mergedIds = List.of(
                "banned-user", "inactive-user", "ai-candidate",
                "fallback-1", "fallback-2", "fallback-3", "fallback-4");
        AiRecommendationResponse response = aiResponse(
                recommendation("banned-user", 0.9, List.of(), false, false, false),
                recommendation("inactive-user", 0.85, List.of(), false, false, false),
                recommendation("ai-candidate", 0.8, List.of(), false, false, false));
        when(userNeo4jRepository.findAllFollowingIds("source")).thenReturn(List.of());
        when(userNeo4jRepository.getRandomSuggestedUser("source", 20)).thenReturn(fallbackIds);
        when(userRepository.findAllById(primaryIds)).thenReturn(List.of(bannedUser, inactiveUser, aiCandidate));
        when(userRepository.findAllById(mergedIds)).thenReturn(List.of(
                bannedUser, inactiveUser, aiCandidate,
                user("fallback-1", "Fallback 1"), user("fallback-2", "Fallback 2"),
                user("fallback-3", "Fallback 3"), user("fallback-4", "Fallback 4")));

        try (MockedConstruction<RestTemplate> ignored = org.mockito.Mockito.mockConstruction(
                RestTemplate.class,
                (mock, context) -> when(mock.getForObject(anyString(), eq(AiRecommendationResponse.class)))
                        .thenReturn(response))) {
            List<FollowResponse> suggestions = followService.getWhoToFollow("source");

            assertEquals(List.of("ai-candidate", "fallback-1", "fallback-2", "fallback-3", "fallback-4"),
                    suggestions.stream().map(FollowResponse::getId).toList());
        }
    }

    private AiRecommendationResponse aiResponse(AiRecommendationResponse.Recommendation... recommendations) {
        AiRecommendationResponse response = new AiRecommendationResponse();
        response.setStatus("success");
        response.setRecommendations(List.of(recommendations));
        return response;
    }

    private AiRecommendationResponse.Recommendation recommendation(
            String userId, double score, List<String> commonInterests,
            boolean sameMajor, boolean sameInterest, boolean sameGroup) {
        AiRecommendationResponse.Features features = new AiRecommendationResponse.Features();
        features.setSameMajor(sameMajor);
        features.setSameInterest(sameInterest);
        features.setSameGroup(sameGroup);

        AiRecommendationResponse.Recommendation recommendation = new AiRecommendationResponse.Recommendation();
        recommendation.setUserId(userId);
        recommendation.setScore(score);
        recommendation.setCommonInterests(commonInterests);
        recommendation.setFeatures(features);
        return recommendation;
    }

    private User user(String id, String fullName) {
        return User.builder()
                .id(id)
                .fullName(fullName)
                .email(id + "@example.com")
                .password("encoded-password")
                .build();
    }
}
