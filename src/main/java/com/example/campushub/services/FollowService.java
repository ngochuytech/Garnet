package com.example.campushub.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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

import com.example.campushub.dtos.record.users.UserFollowPayload;
import com.example.campushub.enums.Neo4jEventType;
import com.example.campushub.enums.NotificationType;
import com.example.campushub.enums.UserStatus;
import com.example.campushub.events.NotificationEvent;
import com.example.campushub.exceptions.ResourceNotFoundException;
import com.example.campushub.exceptions.BadRequestException;
import com.example.campushub.models.jpa.Neo4jSyncEvent;
import com.example.campushub.models.jpa.Notification;
import com.example.campushub.models.jpa.User;
import com.example.campushub.models.jpa.UserBlock;
import com.example.campushub.models.jpa.UserBlockId;
import com.example.campushub.models.jpa.UserFollow;
import com.example.campushub.models.jpa.UserFollowId;
import com.example.campushub.repositories.jpa.Neo4jSyncEventRepository;
import com.example.campushub.repositories.jpa.NotificationRepository;
import com.example.campushub.repositories.jpa.UserBlockRepository;
import com.example.campushub.repositories.jpa.UserFollowRepository;
import com.example.campushub.repositories.jpa.UserRepository;
import com.example.campushub.repositories.neo4j.UserNeo4jRepository;
import com.example.campushub.repositories.neo4j.projections.FriendSuggestionCandidate;
import com.example.campushub.responses.FollowResponse;
import com.example.campushub.responses.FollowStats;
import com.example.campushub.responses.FriendSuggestionPageResponse;
import com.example.campushub.responses.profiles.AnotherUserResponse;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class FollowService {
    private static final int WHO_TO_FOLLOW_LIMIT = 5;
    private static final int WHO_TO_FOLLOW_CANDIDATE_POOL_SIZE = 30;

    private final UserNeo4jRepository userNeo4jRepository;
    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;
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
            throw new BadRequestException("Không thể tự theo dõi chính mình");
        }
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng hiện tại không tồn tại"));
        User targetUser = userRepository.findById(targetUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Người dùng bạn theo dõi không tồn tại"));

        if (targetUser.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("Khong thể theo dõi người dùng chưa kích hoạt hoặc đã bị vô hiệu hóa");
        }

        if (userBlockRepository.countBlocksBetween(currentUserId, targetUserId) > 0) {
            throw new BadRequestException("Không thể follow người dùng này");
        }

        UserFollowId followId = new UserFollowId(currentUserId, targetUserId);
        if (userFollowRepository.existsById(followId)) {
            throw new BadRequestException("Bạn đã theo dõi người dùng này");
        }

        LocalDateTime followedAt = LocalDateTime.now();
        userFollowRepository.save(UserFollow.builder()
            .id(followId)
            .follower(currentUser)
            .target(targetUser)
            .createdAt(followedAt)
            .build());

        neo4jSyncEventRepository.save(Neo4jSyncEvent.pending(
            Neo4jEventType.USER_FOLLOWED, 
            currentUserId + ":" + targetUserId, 
            toJson(new UserFollowPayload(currentUserId, targetUserId, followedAt))));

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
            throw new BadRequestException("Không thể bỏ theo dõi chính mình");
        }
        if (!userRepository.existsById(targetUserId)) {
            throw new ResourceNotFoundException("Người dùng bạn bỏ theo dõi không tồn tại");
        }

        userFollowRepository.deleteById(new UserFollowId(currentUserId, targetUserId));
        
        neo4jSyncEventRepository.save(Neo4jSyncEvent.pending(
                Neo4jEventType.USER_UNFOLLOWED,
                currentUserId + ":" + targetUserId,
                toJson(new UserFollowPayload(currentUserId, targetUserId, LocalDateTime.now()))
        ));

        Optional<Notification> oldOptional = notificationRepository
                .findFirstByRecipientIdAndActorIdAndType(targetUserId, currentUserId, NotificationType.NEW_FOLLOWER);
        if (oldOptional.isPresent()) {
            notificationRepository.delete(oldOptional.get());
        }
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void blockUser(String currentUserId, String targetUserId) throws Exception {
        if (currentUserId.equals(targetUserId)) {
            throw new BadRequestException("Cannot block yourself");
        }

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Current user does not exist"));
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Target user does not exist"));

        UserBlockId blockId = new UserBlockId(currentUserId, targetUserId);
        if (!userBlockRepository.existsById(blockId)) {
            userBlockRepository.save(UserBlock.builder()
                    .id(blockId)
                    .blocker(currentUser)
                    .blocked(targetUser)
                    .build());
        }

        removeFollowForBlock(currentUserId, targetUserId);
        removeFollowForBlock(targetUserId, currentUserId);
    }

    @Transactional("transactionManager")
    public void unblockUser(String currentUserId, String targetUserId) throws Exception {
        if (currentUserId.equals(targetUserId)) {
            throw new BadRequestException("Cannot unblock yourself");
        }
        if (!userRepository.existsById(targetUserId)) {
            throw new ResourceNotFoundException("Target user does not exist");
        }

        userBlockRepository.deleteById(new UserBlockId(currentUserId, targetUserId));
    }

    public FriendSuggestionPageResponse getWhoToFollow(String userId) {
        List<FriendSuggestionCandidate> candidates = new ArrayList<>(
                userNeo4jRepository.findFriendSuggestionCandidates(
                        userId,
                        WHO_TO_FOLLOW_CANDIDATE_POOL_SIZE,
                        WHO_TO_FOLLOW_LIMIT));

        if (candidates.size() < WHO_TO_FOLLOW_LIMIT) {
            List<String> excludedUserIds = candidates.stream()
                    .map(FriendSuggestionCandidate::getId)
                    .toList();
            candidates.addAll(userNeo4jRepository.findRandomFriendSuggestionCandidates(
                    userId,
                    excludedUserIds,
                    WHO_TO_FOLLOW_LIMIT - candidates.size()));
        }

        List<FollowResponse> items = candidates.stream()
                .limit(WHO_TO_FOLLOW_LIMIT)
                .map(candidate -> FollowResponse.builder()
                        .id(candidate.getId())
                        .fullName(candidate.getFullName())
                        .avatarUrl(candidate.getAvatarUrl())
                        .department(candidate.getMajorName())
                        .build())
                .toList();

        return FriendSuggestionPageResponse.builder()
                .items(items)
                .build();
    }

    private void removeFollowForBlock(String followerId, String targetUserId) {
        UserFollowId followId = new UserFollowId(followerId, targetUserId);
        if (!userFollowRepository.existsById(followId)) {
            return;
        }

        userFollowRepository.deleteById(followId);
        neo4jSyncEventRepository.save(Neo4jSyncEvent.pending(
                Neo4jEventType.USER_UNFOLLOWED,
                followerId + ":" + targetUserId,
                toJson(new UserFollowPayload(followerId, targetUserId, LocalDateTime.now()))));

        notificationRepository.findFirstByRecipientIdAndActorIdAndType(
                targetUserId, followerId, NotificationType.NEW_FOLLOWER)
                .ifPresent(notificationRepository::delete);
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
            throw new BadRequestException("Lỗi khi đếm số lượng người theo dõi và đang theo dõi");
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
