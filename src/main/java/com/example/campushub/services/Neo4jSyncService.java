package com.example.campushub.services;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.campushub.dtos.record.groups.GroupCreatedPayload;
import com.example.campushub.dtos.record.groups.GroupDeletedPayload;
import com.example.campushub.dtos.record.groups.GroupMemberApprovedPayload;
import com.example.campushub.dtos.record.groups.GroupMemberRemovedPayload;
import com.example.campushub.dtos.record.groups.GroupNameUpdatedPayload;
import com.example.campushub.dtos.record.posts.PostCommentChangedPayload;
import com.example.campushub.dtos.record.posts.PostCreatedPayload;
import com.example.campushub.dtos.record.posts.PostReactionChangedPayload;
import com.example.campushub.dtos.record.posts.PostStatusChangedPayload;
import com.example.campushub.dtos.record.posts.PostSharedPayload;
import com.example.campushub.dtos.record.profiles.UserProfileUpdatedPayload;
import com.example.campushub.dtos.record.users.UserFollowPayload;
import com.example.campushub.dtos.record.users.UserStatusChangedPayload;
import com.example.campushub.enums.ContentStatus;
import com.example.campushub.enums.Neo4jEventStatus;
import com.example.campushub.enums.ReactionType;
import com.example.campushub.models.jpa.Comment;
import com.example.campushub.models.jpa.Neo4jSyncEvent;
import com.example.campushub.models.jpa.PostReaction;
import com.example.campushub.models.jpa.PostReactionId;
import com.example.campushub.models.jpa.UserFollow;
import com.example.campushub.models.jpa.UserFollowId;
import com.example.campushub.models.neo4j.GroupNode;
import com.example.campushub.repositories.jpa.CommentRepository;
import com.example.campushub.repositories.jpa.Neo4jSyncEventRepository;
import com.example.campushub.repositories.jpa.PostReactionRepository;
import com.example.campushub.repositories.jpa.UserFollowRepository;
import com.example.campushub.repositories.neo4j.GroupNeo4jRepository;
import com.example.campushub.repositories.neo4j.PostNeo4jRepository;
import com.example.campushub.repositories.neo4j.UserNeo4jRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class Neo4jSyncService {
    private final Neo4jSyncEventRepository neo4jSyncEventRepository;
    private final PostNeo4jRepository postNeo4jRepository;
    private final GroupNeo4jRepository groupNeo4jRepository;
    private final UserNeo4jRepository userNeo4jRepository;
    private final UserFollowRepository userFollowRepository;
    private final PostReactionRepository postReactionRepository;
    private final CommentRepository commentRepository;
    private final ObjectMapper objectMapper;

    @Transactional(value = "transactionManager")
    public void process(Neo4jSyncEvent event) {
        try {
            switch (event.getEventType().name()) {
                case "POST_CREATED" -> syncPostCreated(event);
                case "POST_SHARED" -> syncPostShared(event);
                case "POST_STATUS_CHANGED" -> syncPostStatusChanged(event);
                case "POST_REACTION_CHANGED" -> syncPostReactionChanged(event);
                case "POST_COMMENT_CHANGED" -> syncPostCommentChanged(event);
                case "GROUP_CREATED" -> syncGroupCreated(event);
                case "GROUP_MEMBER_APPROVED" -> syncGroupMemberApproved(event);
                case "GROUP_MEMBER_REMOVED" -> syncGroupMemberRemovd(event);
                case "GROUP_DELETED" -> syncGroupDeleted(event);
                case "GROUP_NAME_UPDATED" -> syncGroupNameUpdated(event);
                case "USER_PROFILE_UPDATED" -> syncUserProfileUpdated(event);
                case "USER_STATUS_CHANGED" -> syncUserStatusChanged(event);
                case "USER_FOLLOWED" -> syncUserFollowed(event);
                case "USER_UNFOLLOWED" -> syncUserUnfollowed(event);
                default -> throw new IllegalArgumentException(
                        "Unsupported Neo4j sync event type" + event.getEventType().name());
            }

            event.setStatus(Neo4jEventStatus.DONE);
            event.setProcessedAt(LocalDateTime.now());
            event.setLastError(null);
        } catch (Exception e) {
            event.setRetryCount(event.getRetryCount() + 1);
            event.setLastError(e.getMessage());

            if (event.getRetryCount() >= 5) {
                event.setStatus(Neo4jEventStatus.FAILED);
            } else {
                event.setStatus(Neo4jEventStatus.PENDING);
            }
        }
        neo4jSyncEventRepository.save(event);
    }

    @Transactional(value = "transactionManager")
    public boolean claim(String eventId) {
        return neo4jSyncEventRepository.markProcessingIfPending(
                eventId,
                Neo4jEventStatus.PENDING,
                Neo4jEventStatus.PROCESSING) == 1;
    }

    private void syncPostCreated(Neo4jSyncEvent event) {
        PostCreatedPayload payload = objectMapper.readValue(event.getPayload(), PostCreatedPayload.class);
        postNeo4jRepository.createPost(
                payload.authorId(),
                payload.postId(),
                payload.tagNames(),
                payload.createdAt());

        if (payload.groupId() != null) {
            postNeo4jRepository.linkPostToGroup(payload.postId(), payload.groupId());
        }
    }

    private void syncPostShared(Neo4jSyncEvent event) {
        PostSharedPayload payload = objectMapper.readValue(event.getPayload(), PostSharedPayload.class);

        long createdPostCount = postNeo4jRepository.createSharedPost(
                payload.sharerId(),
                payload.sharedPostId(),
                payload.originalPostId(),
                payload.tagNames(),
                payload.createdAt());

        if (createdPostCount != 1) {
            throw new IllegalStateException("Neo4j did not create the shared-post graph relation");
        }
    }

    private void syncPostStatusChanged(Neo4jSyncEvent event) {
        PostStatusChangedPayload payload = objectMapper.readValue(event.getPayload(), PostStatusChangedPayload.class);

        long updatedCount = postNeo4jRepository.updatePostStatus(payload.postId(), payload.status().name());

        if (updatedCount != 1) {
            throw new IllegalStateException("Neo4j did not update the post status");
        }
    }

    private void syncGroupCreated(Neo4jSyncEvent event) {
        GroupCreatedPayload payload = objectMapper.readValue(
                event.getPayload(),
                GroupCreatedPayload.class);

        GroupNode groupNode = GroupNode.builder()
                .id(payload.groupId())
                .name(payload.groupName())
                .build();

        groupNeo4jRepository.save(groupNode);
        groupNeo4jRepository.addUserToGroup(payload.leaderId(), payload.groupId());
    }

    private void syncGroupMemberApproved(Neo4jSyncEvent event) {
        GroupMemberApprovedPayload payload = objectMapper.readValue(
                event.getPayload(),
                GroupMemberApprovedPayload.class);

        long linkedCount = groupNeo4jRepository.addExistingUserToGroup(payload.userId(), payload.groupId());
        if (linkedCount != 1) {
            throw new IllegalStateException("Neo4j did not link the user to the group");
        }
    }

    private void syncGroupMemberRemovd(Neo4jSyncEvent event) {
        GroupMemberRemovedPayload payload = objectMapper.readValue(
                event.getPayload(),
                GroupMemberRemovedPayload.class);

        groupNeo4jRepository.removeUserFromGroup(payload.userId(), payload.groupId());
    }

    private void syncGroupDeleted(Neo4jSyncEvent event) {
        GroupDeletedPayload payload = objectMapper.readValue(
                event.getPayload(),
                GroupDeletedPayload.class);

        groupNeo4jRepository.deleteGroupById(payload.groupId());
    }

    private void syncGroupNameUpdated(Neo4jSyncEvent event) {
        GroupNameUpdatedPayload payload = objectMapper.readValue(
                event.getPayload(),
                GroupNameUpdatedPayload.class);

        long updatedCount = groupNeo4jRepository.updateGroupName(payload.groupId(), payload.name());

        if (updatedCount != 1) {
            throw new IllegalStateException("Neo4j did not update the group name");
        }
    }

    private void syncUserProfileUpdated(Neo4jSyncEvent event) {
        UserProfileUpdatedPayload payload = objectMapper.readValue(
                event.getPayload(),
                UserProfileUpdatedPayload.class);

        userNeo4jRepository.replaceUserProfileGraph(
                payload.userId(),
                payload.major(),
                payload.hobbies(),
                payload.status() != null ? payload.status().name() : null);
    }

    private void syncUserStatusChanged(Neo4jSyncEvent event) {
        UserStatusChangedPayload payload = objectMapper.readValue(event.getPayload(), UserStatusChangedPayload.class);
        long updatedCount = userNeo4jRepository.updateUserStatus(payload.userId(), payload.status().name());

        if (updatedCount != 1) {
            throw new IllegalStateException("Neo4j did not update the user status");
        }
    }

    private void syncUserFollowed(Neo4jSyncEvent event) {
        UserFollowPayload payload = objectMapper.readValue(
                event.getPayload(),
                UserFollowPayload.class);

        UserFollow follow = userFollowRepository.findById(
                new UserFollowId(payload.followerId(), payload.targetId())).orElse(null);

        if (follow == null) {
            return;
        }
        LocalDateTime createdAt = payload.createdAt() != null ? payload.createdAt() : follow.getCreatedAt();
        boolean success = userNeo4jRepository.followUser(payload.followerId(), payload.targetId(), createdAt);
        if (!success) {
            throw new IllegalStateException("Neo4j did not create follow relation");
        }
    }

    private void syncUserUnfollowed(Neo4jSyncEvent event) {
        UserFollowPayload payload = objectMapper.readValue(event.getPayload(), UserFollowPayload.class);

        userNeo4jRepository.unfollowUser(payload.followerId(), payload.targetId());
    }

    private void syncPostReactionChanged(Neo4jSyncEvent event) {
        PostReactionChangedPayload payload = objectMapper.readValue(event.getPayload(), PostReactionChangedPayload.class);

        PostReaction postReaction = postReactionRepository.findById(new PostReactionId(payload.postId(), payload.userId()))
                .orElse(null);

        if (postReaction != null && postReaction.getType() == ReactionType.LIKE) {
            LocalDateTime createdAt = payload.createdAt() != null
                    ? payload.createdAt()
                    : postReaction.getCreatedAt();
            long createdRelationCount = postNeo4jRepository.createLikedRelation(
                    payload.userId(), payload.postId(), createdAt);
            if (createdRelationCount != 1) {
                throw new IllegalStateException("Neo4j did not create the liked relation");
            }
        } else {
            postNeo4jRepository.deleteLikedRelation(payload.userId(), payload.postId());
        }
    }

    private void syncPostCommentChanged(Neo4jSyncEvent event) {
        PostCommentChangedPayload payload = objectMapper.readValue(event.getPayload(), PostCommentChangedPayload.class);
        Comment comment = commentRepository.findById(payload.commentId()).orElse(null);

        if (comment == null) {
            return;
        }

        String userId = comment.getUser().getId();
        String postId = comment.getPost().getId();
        Optional<Comment> firstActiveComment = commentRepository
                .findFirstByUser_IdAndPost_IdAndStatusOrderByCreatedAtAsc(userId, postId, ContentStatus.ACTIVE);

        if (firstActiveComment.isPresent()) {
            long createdRelationCount = postNeo4jRepository.createCommentedRelation(
                    userId, postId, firstActiveComment.get().getCreatedAt());
            if (createdRelationCount != 1) {
                throw new IllegalStateException("Neo4j did not create the commented relation");
            }
        } else {
            postNeo4jRepository.deleteCommentedRelation(userId, postId);
        }
    }
}
