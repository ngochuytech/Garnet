package com.example.campushub.services;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import com.example.campushub.dtos.AiRecommendationResponse;
import com.example.campushub.enums.NotificationType;
import com.example.campushub.events.NotificationEvent;
import com.example.campushub.exceptions.DataNotFoundException;
import com.example.campushub.exceptions.InvalidParamException;
import com.example.campushub.models.jpa.Notification;
import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.NotificationRepository;
import com.example.campushub.repositories.jpa.UserRepository;
import com.example.campushub.repositories.neo4j.UserNeo4jRepository;
import com.example.campushub.responses.FollowResponse;
import com.example.campushub.responses.FollowStats;
import com.example.campushub.responses.profiles.AnotherUserResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FollowService {
    private final UserNeo4jRepository userNeo4jRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public void followUser(String currentUserId, String targetUserId) throws Exception {
        if (currentUserId.equals(targetUserId)) {
            throw new InvalidParamException("Không thể tự theo dõi chính mình");
        }

        User targetUser = userRepository.findById(targetUserId)
            .orElseThrow(() -> new DataNotFoundException("Người dùng bạn theo dõi không tồn tại"));

        boolean isSuccess = userNeo4jRepository.followUser(currentUserId, targetUserId);
        if (!isSuccess) {
            throw new InvalidParamException("Đã xảy ra lỗi khi theo dõi người dùng");
        }

        User currentUser = userRepository.findById(currentUserId).orElse(null);
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
        userNeo4jRepository.unfollowUser(currentUserId, targetUserId);

        Optional<Notification> oldOptional = notificationRepository.findFirstByRecipientIdAndActorIdAndType(targetUserId, currentUserId, NotificationType.NEW_FOLLOWER);
        if(oldOptional.isPresent()) {
            notificationRepository.delete(oldOptional.get());
        }
    }

    public List<User> getWhoToFollow(String userId) {
        List<String> suggestedIds;
        List<String> followedIds = userNeo4jRepository.findAllFollowingIds(userId);
        
        try {
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            String aiUrl = "http://localhost:8000/recommend/" + userId;
            if (followedIds != null && !followedIds.isEmpty()) {
                String excludeParam = String.join(",", followedIds);
                aiUrl += "?exclude=" + excludeParam;
            }
            
            AiRecommendationResponse aiResponse = restTemplate.getForObject(aiUrl, AiRecommendationResponse.class);
            if (aiResponse != null && "success".equals(aiResponse.getStatus()) && aiResponse.getRecommendations() != null) {
                suggestedIds = aiResponse.getRecommendations().stream()
                        .map(AiRecommendationResponse.Recommendation::getUserId)
                        .toList();
            } else {
                suggestedIds = userNeo4jRepository.getRandomSuggestedUser(userId);
            }
        } catch (Exception e) {
            suggestedIds = userNeo4jRepository.getSuggestedUserByHooby(userId);
            if (suggestedIds.isEmpty() || suggestedIds.size() < 5) {
                suggestedIds = userNeo4jRepository.getRandomSuggestedUser(userId);
            }
        }
        
        List<User> users = userRepository.findAllById(suggestedIds);
        // Sắp xếp lại theo thứ tự của suggestedIds (đảm bảo giữ nguyên thứ tự recommend từ AI)
        Map<String, User> userMap = users.stream().collect(Collectors.toMap(User::getId, user -> user));
        return suggestedIds.stream()
                .map(userMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
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
