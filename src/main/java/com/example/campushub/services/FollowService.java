package com.example.campushub.services;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.example.campushub.exceptions.InvalidParamException;
import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.UserRepository;
import com.example.campushub.repositories.neo4j.UserNeo4jRepository;
import com.example.campushub.responses.FollowResponse;
import com.example.campushub.responses.FollowStats;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FollowService {
    private final UserNeo4jRepository userNeo4jRepository;
    private final UserRepository userRepository;

    public void followUser(String currentUserId, String targetUserId) throws Exception {
        if (currentUserId.equals(targetUserId)) {
            throw new InvalidParamException("Không thể tự theo dõi chính mình");
        }
        if (!userRepository.existsById(targetUserId)) {
            throw new InvalidParamException("Người dùng bạn theo dõi không tồn tại");
        }

        boolean isSuccess = userNeo4jRepository.followUser(currentUserId, targetUserId);
        if (!isSuccess) {
            throw new InvalidParamException("Đã xảy ra lỗi khi theo dõi người dùng");
        }

    }

    public void unfollowUser(String currentUserId, String targetUserId) throws Exception {
        if (currentUserId.equals(targetUserId)) {
            throw new InvalidParamException("Không thể bỏ theo dõi chính mình");
        }
        if (!userRepository.existsById(targetUserId)) {
            throw new InvalidParamException("Người dùng bạn bỏ theo dõi không tồn tại");
        }
        userNeo4jRepository.unfollowUser(currentUserId, targetUserId);
    }

    public List<User> getWhoToFollow(String userId) {
        List<String> suggestedIds = userNeo4jRepository.getSuggestedUserByHooby(userId);
        if (suggestedIds.isEmpty() || suggestedIds.size() < 5) {
            suggestedIds = userNeo4jRepository.getRandomSuggestedUser(userId);
        }
        List<User> suggestedUser = userRepository.findAllById(suggestedIds);
        return suggestedUser;
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
        if(stats == null) {
            throw new InvalidParamException("Lỗi khi đếm số lượng người theo dõi và đang theo dõi");
        }
        return stats;
    }

    public boolean checkIfFollowing(String currentUserId, String targetUserId) {
        return userNeo4jRepository.isFollowing(currentUserId, targetUserId);
    }
}
