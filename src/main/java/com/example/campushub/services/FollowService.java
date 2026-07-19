package com.example.campushub.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.campushub.dtos.AiRecommendationResponse;
import com.example.campushub.dtos.record.users.UserFollowPayload;
import com.example.campushub.enums.Neo4jEventType;
import com.example.campushub.enums.NotificationType;
import com.example.campushub.enums.UserStatus;
import com.example.campushub.events.NotificationEvent;
import com.example.campushub.exceptions.DataNotFoundException;
import com.example.campushub.exceptions.InvalidParamException;
import com.example.campushub.models.jpa.Neo4jSyncEvent;
import com.example.campushub.models.jpa.Notification;
import com.example.campushub.models.jpa.User;
import com.example.campushub.models.jpa.UserFollow;
import com.example.campushub.models.jpa.UserFollowId;
import com.example.campushub.repositories.jpa.Neo4jSyncEventRepository;
import com.example.campushub.repositories.jpa.NotificationRepository;
import com.example.campushub.repositories.jpa.UserFollowRepository;
import com.example.campushub.repositories.jpa.UserRepository;
import com.example.campushub.repositories.neo4j.UserNeo4jRepository;
import com.example.campushub.responses.FollowResponse;
import com.example.campushub.responses.FollowStats;
import com.example.campushub.responses.profiles.AnotherUserResponse;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class FollowService {
    private static final int WHO_TO_FOLLOW_LIMIT = 5;
    private static final int WHO_TO_FOLLOW_CANDIDATE_LIMIT = 20;

    private final UserNeo4jRepository userNeo4jRepository;
    private final UserRepository userRepository;
    private final UserFollowRepository userFollowRepository;
    private final NotificationRepository notificationRepository;
    private final Neo4jSyncEventRepository neo4jSyncEventRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    private String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert object to JSON", e);
        }
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void followUser(String currentUserId, String targetUserId) throws Exception {
        if (currentUserId.equals(targetUserId)) {
            throw new InvalidParamException("Không thể tự theo dõi chính mình");
        }
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new DataNotFoundException("Người dùng hiện tại không tồn tại"));
        User targetUser = userRepository.findById(targetUserId)
            .orElseThrow(() -> new DataNotFoundException("Người dùng bạn theo dõi không tồn tại"));

        if (targetUser.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidParamException("Khong thể theo dõi người dùng chưa kích hoạt hoặc đã bị vô hiệu hóa");
        }

        UserFollowId followId = new UserFollowId(currentUserId, targetUserId);
        if (userFollowRepository.existsById(followId)) {
            throw new InvalidParamException("Bạn đã theo dõi người dùng này");
        }

        userFollowRepository.save(UserFollow.builder()
            .id(followId)
            .follower(currentUser)
            .target(targetUser)
            .build());

        neo4jSyncEventRepository.save(Neo4jSyncEvent.pending(
            Neo4jEventType.USER_FOLLOWED, 
            currentUserId + ":" + targetUserId, 
            toJson(new UserFollowPayload(currentUserId, targetUserId))));

        if (currentUser != null) {
            NotificationEvent event = NotificationEvent.builder()
                    .recipientId(targetUserId)
                    .recipientName(targetUser.getUsername())
                    .actorId(currentUserId)
                    .type(NotificationType.NEW_FOLLOWER)
                    .targetType("USER")
                    .targetId(targetUserId)
                    .message(currentUser.getFullName() + " đã bắt đầu theo dõi bạn!")
                    .build();
            eventPublisher.publishEvent(event);
        }

    }

    @Transactional("transactionManager")
    public void unfollowUser(String currentUserId, String targetUserId) throws Exception {
        if (currentUserId.equals(targetUserId)) {
            throw new InvalidParamException("Không thể bỏ theo dõi chính mình");
        }
        if (!userRepository.existsById(targetUserId)) {
            throw new InvalidParamException("Người dùng bạn bỏ theo dõi không tồn tại");
        }

        userFollowRepository.deleteById(new UserFollowId(currentUserId, targetUserId));
        
        neo4jSyncEventRepository.save(Neo4jSyncEvent.pending(
                Neo4jEventType.USER_UNFOLLOWED,
                currentUserId + ":" + targetUserId,
                toJson(new UserFollowPayload(currentUserId, targetUserId))
        ));

        Optional<Notification> oldOptional = notificationRepository
                .findFirstByRecipientIdAndActorIdAndType(targetUserId, currentUserId, NotificationType.NEW_FOLLOWER);
        if (oldOptional.isPresent()) {
            notificationRepository.delete(oldOptional.get());
        }
    }

    public List<FollowResponse> getWhoToFollow(String userId) {
        List<String> suggestedIds = java.util.Collections.emptyList();
        List<String> followedIds = userNeo4jRepository.findAllFollowingIds(userId);
        Map<String, AiRecommendationResponse.Recommendation> aiRecMap = new HashMap<>();

        try {
            RestTemplate restTemplate = new RestTemplate();
            UriComponentsBuilder uriBuilder = UriComponentsBuilder
                    .fromUriString("http://localhost:8000/recommend/{userId}")
                    .queryParam("top_k", WHO_TO_FOLLOW_CANDIDATE_LIMIT)
                    .queryParam("exclude", "all");

            if (followedIds != null && !followedIds.isEmpty()) {
                uriBuilder.queryParam("exclude_user_ids", String.join(",", followedIds));
            }

            String aiUrl = uriBuilder.buildAndExpand(userId).toUriString();
            AiRecommendationResponse aiResponse = restTemplate.getForObject(aiUrl, AiRecommendationResponse.class);
            if (aiResponse != null && "success".equals(aiResponse.getStatus())
                    && aiResponse.getRecommendations() != null) {
                suggestedIds = aiResponse.getRecommendations().stream()
                        .map(AiRecommendationResponse.Recommendation::getUserId)
                        .filter(Objects::nonNull)
                        .filter(id -> !id.isBlank())
                        .distinct()
                        .toList();

                for (AiRecommendationResponse.Recommendation rec : aiResponse.getRecommendations()) {
                    String recommendationUserId = rec.getUserId();
                    if (recommendationUserId != null && !recommendationUserId.isBlank()) {
                        aiRecMap.put(recommendationUserId, rec);
                    }
                }

            }
        } catch (Exception e) {
            suggestedIds = userNeo4jRepository.getSuggestedUserByHooby(userId, WHO_TO_FOLLOW_CANDIDATE_LIMIT);
        }

        List<User> eligibleUsers = findEligibleSuggestionUsers(suggestedIds, userId, followedIds);
        if (eligibleUsers.size() < WHO_TO_FOLLOW_LIMIT) {
            List<String> fallbackIds = userNeo4jRepository.getRandomSuggestedUser(
                    userId, WHO_TO_FOLLOW_CANDIDATE_LIMIT);
            eligibleUsers = findEligibleSuggestionUsers(
                    mergeSuggestionIds(suggestedIds, fallbackIds), userId, followedIds);
        }

        return eligibleUsers.stream()
                .map(user -> {
                    FollowResponse.FollowResponseBuilder builder = FollowResponse.builder()
                            .id(user.getId())
                            .fullName(user.getFullName())
                            .avatarUrl(user.getAvatarUrl())
                            .department(user.getDepartment());

                    AiRecommendationResponse.Recommendation rec = aiRecMap.get(user.getId());
                    if (rec != null) {
                        builder.commonInterests(rec.getCommonInterests())
                                .reason(buildSuggestionReasons(rec));
                    }
                    return builder.build();
                })
                .collect(Collectors.toList());
    }

    List<String> mergeSuggestionIds(List<String> primaryIds, List<String> fallbackIds) {
        List<String> mergedIds = new ArrayList<>();
        addSuggestionIds(mergedIds, primaryIds);
        addSuggestionIds(mergedIds, fallbackIds);
        return mergedIds;
    }

    private void addSuggestionIds(List<String> targetIds, List<String> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return;
        }
        for (String id : sourceIds) {
            if (id != null && !id.isBlank() && !targetIds.contains(id)) {
                targetIds.add(id);
            }
        }
    }

    private List<User> findEligibleSuggestionUsers(
            List<String> candidateIds,
            String currentUserId,
            List<String> followedIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> followedIdSet = followedIds == null ? Collections.emptySet() : new HashSet<>(followedIds);
        Map<String, User> userMap = userRepository.findAllById(candidateIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        return candidateIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .filter(id -> !id.equals(currentUserId))
                .filter(id -> !followedIdSet.contains(id))
                .map(userMap::get)
                .filter(Objects::nonNull)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .limit(WHO_TO_FOLLOW_LIMIT)
                .toList();
    }

    List<String> buildSuggestionReasons(AiRecommendationResponse.Recommendation recommendation) {
        if (recommendation == null) {
            return Collections.emptyList();
        }

        List<String> reasons = new ArrayList<>();
        List<String> commonInterests = recommendation.getCommonInterests();
        if (commonInterests != null) {
            commonInterests.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(interest -> !interest.isEmpty())
                    .forEach(reasons::add);
        }

        AiRecommendationResponse.Features features = recommendation.getFeatures();
        if (features == null) {
            return reasons;
        }
        if (Boolean.TRUE.equals(features.getSameMajor())) {
            reasons.add("Cùng ngành");
        }
        if (Boolean.TRUE.equals(features.getSameGroup())) {
            reasons.add("Cùng nhóm");
        }
        return reasons;
    }

    public Page<FollowResponse> searchUsers(String userId, String query, Pageable pageable) {
        Page<User> userResults = userRepository.findByFullNameContainingIgnoreCase(query, pageable);
        return userResults.map(user -> FollowResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .department(user.getDepartment())
                .build());
    }

    public FollowStats countFollowersAndFollowing(String userId) throws Exception {
        FollowStats stats = userNeo4jRepository.getFollowStats(userId);
        if (stats == null) {
            throw new InvalidParamException("Lỗi khi đếm số lượng người theo dõi và đang theo dõi");
        }
        return stats;
    }

    public boolean checkIfFollowing(String currentUserId, String targetUserId) {
        return userNeo4jRepository.isFollowing(currentUserId, targetUserId);
    }

    public Slice<AnotherUserResponse> getFollowingList(String currentUserId, String targetUserId, Pageable pageable) {
        int pagesize = pageable.getPageSize();
        List<String> followingIds = userNeo4jRepository.findFollowingIdsPaging(targetUserId,
                pageable.getOffset(),
                pagesize + 1);

        boolean hasNext = followingIds.size() > pagesize;

        // Nếu có trang tiếp thì bỏ phần tử thừa đi (vd: thứ 11) để hiển thị
        List<String> idsToQuery = hasNext ? followingIds.subList(0, pagesize) : followingIds;

        if (idsToQuery.isEmpty()) {
            return new SliceImpl<>(Collections.emptyList(), pageable, false);
        }

        List<User> users = userRepository.findAllById(idsToQuery);
        Map<String, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        Set<String> alreadyFollowedIds;
        if (currentUserId != null)
            alreadyFollowedIds = userNeo4jRepository.findFollowingIdsInList(currentUserId, idsToQuery);
        else
            alreadyFollowedIds = new HashSet<>();

        List<AnotherUserResponse> responseList = idsToQuery.stream()
                .map(id -> {
                    User u = userMap.get(id);
                    if (u == null)
                        return null;

                    // Kiểm tra xem currentUserId có đang follow user trong list hay không
                    boolean isFollowing = alreadyFollowedIds.contains(id);

                    return AnotherUserResponse.builder()
                            .id(u.getId())
                            .fullname(u.getFullName())
                            .avatarUrl(u.getAvatarUrl())
                            .department(u.getDepartment())
                            .isFollowing(isFollowing)
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return new SliceImpl<>(responseList, pageable, hasNext);
    }

    public Slice<AnotherUserResponse> getFollowerList(String currentUserId, String targetUserId, Pageable pageable) {
        int pagesize = pageable.getPageSize();
        List<String> followingIds = userNeo4jRepository.findFollowerIdsPaging(targetUserId,
                pageable.getOffset(),
                pagesize + 1);

        boolean hasNext = followingIds.size() > pagesize;

        // Nếu có trang tiếp thì bỏ phần tử thừa đi (vd: thứ 11) để hiển thị
        List<String> idsToQuery = hasNext ? followingIds.subList(0, pagesize) : followingIds;

        if (idsToQuery.isEmpty()) {
            return new SliceImpl<>(Collections.emptyList(), pageable, false);
        }

        List<User> users = userRepository.findAllById(idsToQuery);
        Map<String, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        Set<String> alreadyFollowedIds;
        if (currentUserId != null)
            alreadyFollowedIds = userNeo4jRepository.findFollowingIdsInList(currentUserId, idsToQuery);
        else
            alreadyFollowedIds = new HashSet<>();

        List<AnotherUserResponse> responseList = idsToQuery.stream()
                .map(id -> {
                    User u = userMap.get(id);
                    if (u == null)
                        return null;

                    // Kiểm tra xem currentUserId có đang follow user trong list hay không
                    boolean isFollowing = alreadyFollowedIds.contains(id);

                    return AnotherUserResponse.builder()
                            .id(u.getId())
                            .fullname(u.getFullName())
                            .avatarUrl(u.getAvatarUrl())
                            .department(u.getDepartment())
                            .isFollowing(isFollowing)
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return new SliceImpl<>(responseList, pageable, hasNext);
    }

}
