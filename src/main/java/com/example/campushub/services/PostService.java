package com.example.campushub.services;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.campushub.dtos.recommendation.RecommendationFeedResponse;
import com.example.campushub.dtos.recommendation.RecommendationItem;
import com.example.campushub.dtos.record.posts.PostCreatedPayload;
import com.example.campushub.dtos.record.posts.PostReactionChangedPayload;
import com.example.campushub.dtos.record.posts.PostSharedPayload;
import com.example.campushub.dtos.record.posts.PostStats;
import com.example.campushub.dtos.record.posts.PostStatusChangedPayload;
import com.example.campushub.dtos.users.CreatePostDTO;
import com.example.campushub.dtos.users.CreateSharePostDTO;
import com.example.campushub.enums.ContentStatus;
import com.example.campushub.enums.GroupStatus;
import com.example.campushub.enums.MemberRole;
import com.example.campushub.enums.MemberStatus;
import com.example.campushub.enums.Neo4jEventType;
import com.example.campushub.enums.NotificationType;
import com.example.campushub.enums.ReactionType;
import com.example.campushub.enums.UserStatus;
import com.example.campushub.events.NotificationEvent;
import com.example.campushub.exceptions.BadRequestException;
import com.example.campushub.exceptions.ForbiddenException;
import com.example.campushub.exceptions.RecommendationClientException;
import com.example.campushub.exceptions.ResourceNotFoundException;
import com.example.campushub.models.jpa.Group;
import com.example.campushub.models.jpa.GroupMember;
import com.example.campushub.models.jpa.GroupMemberId;
import com.example.campushub.models.jpa.Neo4jSyncEvent;
import com.example.campushub.models.jpa.Post;
import com.example.campushub.models.jpa.PostReaction;
import com.example.campushub.models.jpa.PostReactionId;
import com.example.campushub.models.jpa.PostTag;
import com.example.campushub.models.jpa.PostTagId;
import com.example.campushub.models.jpa.RecommendationOutbox;
import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.CommentRepository;
import com.example.campushub.repositories.jpa.GroupMemberRepository;
import com.example.campushub.repositories.jpa.GroupRepository;
import com.example.campushub.repositories.jpa.Neo4jSyncEventRepository;
import com.example.campushub.repositories.jpa.PostReactionRepository;
import com.example.campushub.repositories.jpa.PostRepository;
import com.example.campushub.repositories.jpa.PostTagRepository;
import com.example.campushub.repositories.jpa.RecommendationOutboxRepository;
import com.example.campushub.repositories.jpa.UserRepository;
import com.example.campushub.repositories.jpa.projections.PostCountProjection;
import com.example.campushub.repositories.jpa.projections.PostMediaProjection;
import com.example.campushub.repositories.jpa.projections.PostReactionCountProjection;
import com.example.campushub.repositories.neo4j.InterestNeo4jRepository;
import com.example.campushub.repositories.neo4j.PostNeo4jRepository;
import com.example.campushub.responses.CursorPagedResponse;
import com.example.campushub.responses.PostResponse;
import com.example.campushub.responses.admin.AdminPostResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {
    private static final int MAX_POST_FEED_PAGE_SIZE = 100;
    private static final String POST_CURSOR_SEPARATOR = "|";
    private static final String FALLBACK_HOME_CURSOR_PREFIX = "fallback:";
    private static final String TOPIC_FALLBACK_CURSOR_PREFIX = "topic-fallback:";
    private static final String GROUP_FALLBACK_CURSOR_PREFIX = "group-fallback:";

    private final PostRepository postRepository;
    private final RecommendationClient recommendationClient;
    private final PostNeo4jRepository postNeo4jRepository;
    private final PostTagRepository postTagRepository;
    private final InterestNeo4jRepository tagNeo4jRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final PostReactionRepository postReactionRepository;
    private final CommentRepository commentRepository;
    private final RecommendationOutboxRepository recommendationOutboxRepository;
    private final Neo4jSyncEventRepository neo4jSyncEventRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    private ContentStatus parseAndValidateContentStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ContentStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Tham số trạng thái bài viết không hợp lệ: " + status);
        }
    }

    private String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert object to JSON", e);
        }
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void createPost(User user, CreatePostDTO dto) throws Exception {
        long existingTagsCount = tagNeo4jRepository.countByNameIn(dto.getTags());
        if (existingTagsCount != dto.getTags().size()) {
            throw new ResourceNotFoundException("Một hoặc nhiều chủ đề không tồn tại");
        }

        Group groupNode = null;
        if (dto.getGroupId() != null) {
            GroupMemberId memberId = new GroupMemberId(dto.getGroupId(), user.getId());
            GroupMember member = groupMemberRepository.findById(memberId)
                    .orElseThrow(() -> new ForbiddenException("Bạn không phải là thành viên của nhóm này"));
            if (member.getStatus() != MemberStatus.APPROVED) {
                throw new ForbiddenException("Bạn chưa phải là thành viên thức của nhóm này");
            }
            groupNode = groupRepository.findById(dto.getGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm"));
            if (groupNode.getStatus() != GroupStatus.ACTIVE) {
                throw new ForbiddenException("Nhóm này đã bị lưu trữ và không còn cho phép đăng bài mới");
            }
        }

        Post post = Post.builder()
                .content(dto.getContent())
                .user(user)
                .group(groupNode)
                .build();

        if (dto.getImageUrls() != null && !dto.getImageUrls().isEmpty()) {
            post.setImages(dto.getImageUrls());
        }
        if (dto.getVideoUrls() != null && !dto.getVideoUrls().isEmpty()) {
            post.setVideos(dto.getVideoUrls());
        }

        postRepository.save(post);

        List<PostTag> postTags = dto.getTags().stream()
                .map(tagName -> PostTag.builder()
                        .id(new PostTagId(post.getId(), tagName))
                        .post(post)
                        .build())
                .toList();
        postTagRepository.saveAll(postTags);

        recommendationOutboxRepository.save(RecommendationOutbox.create(
                RecommendationOutbox.EventType.POST_UPSERT,
                post.getId(),
                null));

        PostCreatedPayload payload = new PostCreatedPayload(
                post.getId(),
                user.getId(),
                dto.getGroupId(),
                new java.util.HashSet<>(dto.getTags()),
                post.getCreatedAt());

        neo4jSyncEventRepository.save(Neo4jSyncEvent.pending(
                Neo4jEventType.POST_CREATED,
                post.getId(),
                toJson(payload)));
    }

    @Transactional("transactionManager")
    public void likePost(User user, String postId) throws Exception {
        Post post = getPostById(postId);
        PostReaction reaction = postReactionRepository.findByPostAndUser(post, user);

        boolean isNewLike = false;

        Map<String, Object> payload = new HashMap<>(Map.of(
                "post_id", post.getId(),
                "action", "LIKE"));

        if (reaction == null) {
            reaction = PostReaction.builder()
                    .id(new PostReactionId(post.getId(), user.getId()))
                    .post(post)
                    .user(user)
                    .type(ReactionType.LIKE)
                    .build();
            postReactionRepository.save(reaction);
            isNewLike = true;

            // + 3 positive
            payload.put("operation", "ADD");
            recommendationOutboxRepository.save(RecommendationOutbox.create(
                    RecommendationOutbox.EventType.USER_INTERACTION,
                    user.getId(),
                    payload));

        } else if (reaction.getType() == ReactionType.DISLIKE) {
            reaction.setType(ReactionType.LIKE);
            postReactionRepository.save(reaction);
            isNewLike = true;

            // + 3 positive
            Map<String, Object> likePayload = new HashMap<>(payload);
            likePayload.put("operation", "ADD");
            RecommendationOutbox likeEvent = RecommendationOutbox.create(
                    RecommendationOutbox.EventType.USER_INTERACTION,
                    user.getId(),
                    likePayload);
            // - 2 negative (remove dislike)
            Map<String, Object> dislikePayload = new HashMap<>(payload);
            dislikePayload.put("action", "DISLIKE");
            dislikePayload.put("operation", "REMOVE");
            RecommendationOutbox dislikeEvent = RecommendationOutbox.create(
                    RecommendationOutbox.EventType.USER_INTERACTION,
                    user.getId(),
                    dislikePayload);
            recommendationOutboxRepository.saveAll(List.of(likeEvent, dislikeEvent));
        } else {
            postReactionRepository.delete(reaction);

            // - 3 positive
            payload.put("operation", "REMOVE");
            recommendationOutboxRepository.save(RecommendationOutbox.create(
                    RecommendationOutbox.EventType.USER_INTERACTION,
                    user.getId(),
                    payload));
        }

        queuePostReactionChangedEvent(user.getId(), post.getId(), LocalDateTime.now());

        if (!user.getId().equals(post.getUser().getId()) && isNewLike) {
            NotificationEvent event = NotificationEvent.builder()
                    .recipientId(post.getUser().getId())
                    .recipientName(post.getUser().getUsername())
                    .actorId(user.getId())
                    .type(NotificationType.LIKE_POST)
                    .targetType("POST")
                    .targetId(post.getId())
                    .message(user.getFullName() + " đã thích bài viết của bạn!")
                    .build();
            eventPublisher.publishEvent(event);
        }
    }

    @Transactional("transactionManager")
    public void dislikePost(User user, String postId) throws Exception {
        Post post = getPostById(postId);
        PostReaction reaction = postReactionRepository.findByPostAndUser(post, user);

        Map<String, Object> payload = new HashMap<>(Map.of(
                "post_id", post.getId(),
                "action", "DISLIKE"));

        if (reaction == null) {
            reaction = PostReaction.builder()
                    .id(new PostReactionId(post.getId(), user.getId()))
                    .post(post)
                    .user(user)
                    .type(ReactionType.DISLIKE)
                    .build();
            postReactionRepository.save(reaction);

            // + 2 negative
            payload.put("operation", "ADD");
            recommendationOutboxRepository.save(RecommendationOutbox.create(
                    RecommendationOutbox.EventType.USER_INTERACTION,
                    user.getId(),
                    payload));
        } else if (reaction.getType() == ReactionType.LIKE) {
            reaction.setType(ReactionType.DISLIKE);
            postReactionRepository.save(reaction);

            // + 2 negative
            Map<String, Object> dislikePayload = new HashMap<>(payload);
            dislikePayload.put("operation", "ADD");
            RecommendationOutbox dislikeEvent = RecommendationOutbox.create(
                    RecommendationOutbox.EventType.USER_INTERACTION,
                    user.getId(),
                    dislikePayload);
            // - 3 positive (remove like)
            Map<String, Object> likePayload = new HashMap<>(payload);
            likePayload.put("action", "LIKE");
            likePayload.put("operation", "REMOVE");
            RecommendationOutbox likeEvent = RecommendationOutbox.create(
                    RecommendationOutbox.EventType.USER_INTERACTION,
                    user.getId(),
                    likePayload);

            recommendationOutboxRepository.saveAll(List.of(dislikeEvent, likeEvent));
        } else {
            postReactionRepository.delete(reaction);
            // - 2 negative
            payload.put("operation", "REMOVE");
            recommendationOutboxRepository.save(RecommendationOutbox.create(
                    RecommendationOutbox.EventType.USER_INTERACTION,
                    user.getId(),
                    payload));
        }

        queuePostReactionChangedEvent(user.getId(), post.getId(), LocalDateTime.now());
    }

    private void queuePostReactionChangedEvent(String userId, String postId, LocalDateTime createdAt) {
        PostReactionChangedPayload payload = new PostReactionChangedPayload(userId, postId, createdAt);
        neo4jSyncEventRepository.save(Neo4jSyncEvent.pending(
                Neo4jEventType.POST_REACTION_CHANGED,
                postId,
                toJson(payload)));
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void sharePost(User user, String postId, CreateSharePostDTO dto) throws Exception {
        // Validate
        Post targetPost = getActivePostById(postId);
        String originalPostId;
        if (targetPost.getSharedPost() != null) {
            originalPostId = targetPost.getSharedPost().getId();
        } else {
            originalPostId = targetPost.getId();
        }
        Post originalPost = getActivePostById(originalPostId);
        boolean hasPreviouslySharedOriginalPost = postRepository.existsByUser_IdAndSharedPost_IdAndStatus(
                user.getId(), originalPost.getId(), ContentStatus.ACTIVE);

        long existingTagsCount = tagNeo4jRepository.countByNameIn(dto.getTags());
        if (existingTagsCount != dto.getTags().size()) {
            throw new ResourceNotFoundException("Một hoặc nhiều chủ đề không tồn tại");
        }

        Post sharedPost = Post.builder()
                .content(dto.getContent())
                .user(user)
                .sharedPost(originalPost)
                .build();
        postRepository.save(sharedPost);

        List<PostTag> postTags = dto.getTags().stream()
                .map(tagName -> PostTag.builder()
                        .id(new PostTagId(sharedPost.getId(), tagName))
                        .post(sharedPost)
                        .build())
                .toList();
        postTagRepository.saveAll(postTags);

        PostSharedPayload payload = new PostSharedPayload(
                sharedPost.getId(),
                user.getId(),
                originalPost.getId(),
                dto.getTags(),
                sharedPost.getCreatedAt());

        neo4jSyncEventRepository.save(Neo4jSyncEvent.pending(
                Neo4jEventType.POST_SHARED,
                sharedPost.getId(),
                toJson(payload)));

        if (!hasPreviouslySharedOriginalPost) {
            recommendationOutboxRepository.save(RecommendationOutbox.create(
                    RecommendationOutbox.EventType.USER_INTERACTION,
                    user.getId(),
                    Map.of(
                            "post_id", originalPost.getId(),
                            "action", "SHARE",
                            "operation", "ADD")));
        }

        if (!user.getId().equals(originalPost.getUser().getId())) {
            NotificationEvent event = NotificationEvent.builder()
                    .recipientId(originalPost.getUser().getId())
                    .recipientName(originalPost.getUser().getUsername())
                    .actorId(user.getId())
                    .type(NotificationType.SHARE_POST)
                    .targetType("POST")
                    .targetId(originalPost.getId())
                    .message(user.getFullName() + " đã chia sẻ bài viết của bạn!")
                    .build();
            eventPublisher.publishEvent(event);
        }
    }

    public Post getActivePostById(String postId) throws Exception {
        return postRepository.findByIdAndStatus(postId, ContentStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Bài viết không tồn tại hoặc đã bị xóa"));
    }

    public PostResponse getActivePostResponseById(String postId, User user) throws Exception {
        Post post = getActivePostById(postId);
        String userReaction = getUserReaction(post, user);
        List<String> tags = getTagsForPost(postId);
        List<String> sharedTags = post.getSharedPost() != null
                ? getTagsForPost(post.getSharedPost().getId())
                : null;
        PostStats stats = getPostStatsMap(List.of(post))
                .getOrDefault(post.getId(), PostStats.empty());
        return PostResponse.fromPost(
                post,
                userReaction,
                tags,
                sharedTags,
                post.getGroup() != null ? post.getGroup().getName() : null,
                post.getSharedPost() != null && post.getSharedPost().getGroup() != null
                        ? post.getSharedPost().getGroup().getName()
                        : null,
                stats);
    }

    public Post getPostById(String postId) throws Exception {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
    }

    public String getUserReaction(Post post, User user) {
        if (user == null)
            return null;
        PostReaction reaction = postReactionRepository.findByPostAndUser(post, user);
        return reaction != null ? reaction.getType().name() : null;
    }

    public List<PostResponse> getMyPostsResponses(User user) throws Exception {
        List<Post> posts = postRepository.findByUserAndStatus(user, ContentStatus.ACTIVE);
        Map<String, String> reactions = getReactionsMap(posts, user);
        Map<String, List<String>> tagsMap = getTagsMap(posts);
        Map<String, PostStats> statsMap = getPostStatsMap(posts);
        return posts.stream()
                .map(post -> toPostResponse(post, reactions, tagsMap, statsMap))
                .collect(Collectors.toList());
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void deletePost(User user, String postId) throws Exception {
        Post post = getPostById(postId);

        boolean canDelete = post.getUser().getId().equals(user.getId());
        if (!canDelete && post.getGroup() != null) {
            GroupMemberId memberId = new GroupMemberId(post.getGroup().getId(), user.getId());
            GroupMember member = groupMemberRepository.findById(memberId).orElse(null);
            if (member != null && member.getRole() == MemberRole.LEADER) {
                canDelete = true;
            }
        }

        if (!canDelete) {
            throw new ForbiddenException("Bạn không có quyền xóa bài viết này");
        }

        boolean isBeingDeleted = post.getStatus() != ContentStatus.DELETED;
        post.setStatus(ContentStatus.DELETED);

        postRepository.save(post);

        if (isBeingDeleted) {
            recommendationOutboxRepository.save(RecommendationOutbox.create(
                    RecommendationOutbox.EventType.POST_INVALIDATE,
                    post.getId(),
                    null));
        }

        PostStatusChangedPayload payload = new PostStatusChangedPayload(
                post.getId(),
                ContentStatus.DELETED);

        neo4jSyncEventRepository.save(Neo4jSyncEvent.pending(
                Neo4jEventType.POST_STATUS_CHANGED, post.getId(), toJson(payload)));
    }

    public CursorPagedResponse<PostResponse> getActivePostsByUserId(
            String userId,
            int size,
            String cursor,
            User currentUser) throws Exception {
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));
        validatePostFeedPageSize(size);

        if (targetUser.getStatus() != UserStatus.ACTIVE) {
            return new CursorPagedResponse<>(List.of(), size, null, false);
        }

        PostCursor decodedCursor = decodePostCursor(cursor);
        int limitPlusOne = size + 1;
        Pageable limit = PageRequest.of(0, limitPlusOne);

        List<Post> candidatePosts = decodedCursor == null
                ? postRepository.findLatestPostsByUserId(
                        userId,
                        ContentStatus.ACTIVE,
                        UserStatus.ACTIVE,
                        limit)
                : postRepository.findLatestPostsByUserIdAfter(
                        userId,
                        ContentStatus.ACTIVE,
                        UserStatus.ACTIVE,
                        decodedCursor.createdAt(),
                        decodedCursor.postId(),
                        limit);
        boolean hasNext = candidatePosts.size() > size;
        List<Post> posts = hasNext
                ? candidatePosts.subList(0, size)
                : candidatePosts;

        Map<String, String> reactions = getReactionsMap(posts, currentUser);
        Map<String, List<String>> tagsMap = getTagsMap(posts);
        Map<String, PostStats> statsMap = getPostStatsMap(posts);
        PostMediaMaps mediaMaps = getPostMediaMaps(posts);
        List<PostResponse> responses = posts.stream()
                .map(post -> toPostResponse(post, reactions, tagsMap, statsMap, mediaMaps))
                .collect(Collectors.toList());

        String nextCursor = hasNext && !posts.isEmpty()
                ? encodePostCursor(posts.getLast())
                : null;
        return new CursorPagedResponse<>(responses, size, nextCursor, nextCursor != null);
    }

    public CursorPagedResponse<PostResponse> getPostsForHomeResponses(
            int size,
            String cursor,
            User user) throws Exception {
        validatePostFeedPageSize(size);

        if (isFallbackHomeCursor(cursor)) {
            return getPostsForHomeFallbackResponses(
                    size,
                    fallbackCursorValue(cursor, FALLBACK_HOME_CURSOR_PREFIX),
                    user);
        }

        try {
            return getRecommendedPostsForHomeResponses(size, cursor, user);
        } catch (RecommendationClientException e) {
            log.warn("Recommendation service is unavailable; using the MySQL home feed fallback: {}", e.getMessage());
            return getPostsForHomeFallbackResponses(size, null, user);
        }
    }

    private CursorPagedResponse<PostResponse> getRecommendedPostsForHomeResponses(
            int size,
            String cursor,
            User user) {
        RecommendationFeedResponse recommendationResponse = recommendationClient
                .getRecommendations(user.getId(), size, cursor);

        List<String> recommendedPostIds = recommendationResponse.items().stream()
                .map(RecommendationItem::postId)
                .filter(postId -> postId != null && !postId.isBlank())
                .toList();

        Map<String, Post> postsById = recommendedPostIds.isEmpty()
                ? Map.of()
                : postRepository.findVisiblePostsByIds(
                        recommendedPostIds,
                        ContentStatus.ACTIVE,
                        UserStatus.ACTIVE).stream()
                        .collect(Collectors.toMap(Post::getId, Function.identity()));

        // Do not sort this list: the Python service already determined the ranking order.
        List<Post> posts = recommendedPostIds.stream()
                .map(postsById::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<String, String> reactions = getReactionsMap(posts, user);
        Map<String, List<String>> tagsMap = getTagsMap(posts);
        Map<String, PostStats> statsMap = getPostStatsMap(posts);
        PostMediaMaps mediaMaps = getPostMediaMaps(posts);
        List<PostResponse> responses = posts.stream()
                .map(post -> toPostResponse(post, reactions, tagsMap, statsMap, mediaMaps))
                .collect(Collectors.toList());

        String nextCursor = normalizeCursor(recommendationResponse.nextCursor());
        return new CursorPagedResponse<>(responses, size, nextCursor, nextCursor != null);
    }

    private CursorPagedResponse<PostResponse> getPostsForHomeFallbackResponses(
            int size,
            String cursor,
            User user) {
        PostCursor decodedCursor = decodePostCursor(cursor);
        int limitPlusOne = size + 1;

        Pageable limit = PageRequest.of(0, limitPlusOne);
        List<Post> candidatePosts = decodedCursor == null
                ? postRepository.findLatestHomeFeedPosts(
                        user.getId(),
                        ContentStatus.ACTIVE,
                        UserStatus.ACTIVE,
                        limit)
                : postRepository.findLatestHomeFeedPostsAfter(
                        user.getId(),
                        ContentStatus.ACTIVE,
                        UserStatus.ACTIVE,
                        decodedCursor.createdAt(),
                        decodedCursor.postId(),
                        limit);

        boolean hasNext = candidatePosts.size() > size;
        List<Post> posts = hasNext
                ? candidatePosts.subList(0, size)
                : candidatePosts;

        Map<String, String> reactions = getReactionsMap(posts, user);
        Map<String, List<String>> tagsMap = getTagsMap(posts);
        Map<String, PostStats> statsMap = getPostStatsMap(posts);
        PostMediaMaps mediaMaps = getPostMediaMaps(posts);
        List<PostResponse> responses = posts.stream()
                .map(post -> toPostResponse(post, reactions, tagsMap, statsMap, mediaMaps))
                .collect(Collectors.toList());

        String nextCursor = hasNext && !posts.isEmpty()
                ? FALLBACK_HOME_CURSOR_PREFIX + encodePostCursor(posts.getLast())
                : null;
        return new CursorPagedResponse<>(responses, size, nextCursor, nextCursor != null);
    }

    private boolean isFallbackHomeCursor(String cursor) {
        return cursor != null && cursor.startsWith(FALLBACK_HOME_CURSOR_PREFIX);
    }

    private boolean isTopicFallbackCursor(String cursor) {
        return cursor != null && cursor.startsWith(TOPIC_FALLBACK_CURSOR_PREFIX);
    }

    private boolean isGroupFallbackCursor(String cursor) {
        return cursor != null && cursor.startsWith(GROUP_FALLBACK_CURSOR_PREFIX);
    }

    private String fallbackCursorValue(String cursor, String prefix) {
        String fallbackCursor = cursor.substring(prefix.length());
        if (fallbackCursor.isBlank()) {
            throw new BadRequestException("Invalid fallback feed cursor");
        }
        return fallbackCursor;
    }

    private String normalizeCursor(String cursor) {
        return cursor == null || cursor.isBlank() ? null : cursor;
    }

    private void validatePostFeedPageSize(int size) {
        if (size < 1 || size > MAX_POST_FEED_PAGE_SIZE) {
            throw new BadRequestException(
                    "Post feed size must be between 1 and " + MAX_POST_FEED_PAGE_SIZE);
        }
    }

    private PostCursor decodePostCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 2);
            if (parts.length != 2 || parts[1].isBlank()) {
                throw new IllegalArgumentException();
            }
            return new PostCursor(LocalDateTime.parse(parts[0]), parts[1]);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new BadRequestException("Invalid post cursor");
        }
    }

    private record PostCursor(LocalDateTime createdAt, String postId) {
    }


    private String encodePostCursor(Post post) {
        return encodePostCursor(post.getCreatedAt(), post.getId());
    }

    private String encodePostCursor(LocalDateTime createdAt, String postId) {
        String rawCursor = createdAt
                + POST_CURSOR_SEPARATOR
                + postId;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(rawCursor.getBytes(StandardCharsets.UTF_8));
    }

    public CursorPagedResponse<PostResponse> getPostsByGroupId(
            String groupId,
            int size,
            String cursor,
            User user) throws Exception {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Nhóm không tồn tại hoặc đã bị xóa"));
        if (group.getStatus() == GroupStatus.DELETED) {
            throw new ResourceNotFoundException("Nhóm không tồn tại hoặc đã bị xóa");
        }
        validatePostFeedPageSize(size);
        if (isGroupFallbackCursor(cursor)) {
            return getPostsByGroupIdFallback(
                    groupId,
                    size,
                    fallbackCursorValue(cursor, GROUP_FALLBACK_CURSOR_PREFIX),
                    user);
        }

        try {
            return getRecommendedPostsByGroupId(groupId, size, cursor, user);
        } catch (RecommendationClientException e) {
            log.warn("Group recommendation service is unavailable; using the MySQL group fallback: {}", e.getMessage());
            return getPostsByGroupIdFallback(groupId, size, null, user);
        }
    }

    private CursorPagedResponse<PostResponse> getRecommendedPostsByGroupId(
            String groupId,
            int size,
            String cursor,
            User user) {
        RecommendationFeedResponse recommendationResponse = recommendationClient
                .getGroupRecommendations(user.getId(), groupId, size, cursor);

        List<String> recommendedPostIds = recommendationResponse.items().stream()
                .map(RecommendationItem::postId)
                .filter(postId -> postId != null && !postId.isBlank())
                .toList();

        Map<String, Post> postsById = recommendedPostIds.isEmpty()
                ? Map.of()
                : postRepository.findActivePostsByIdsAndGroupId(
                        recommendedPostIds,
                        groupId,
                        ContentStatus.ACTIVE,
                        UserStatus.ACTIVE).stream()
                        .collect(Collectors.toMap(Post::getId, Function.identity()));

        // the Python service already determined the ranking order.
        List<Post> posts = recommendedPostIds.stream()
                .map(postsById::get)
                .filter(java.util.Objects::nonNull)
                .toList();

        Map<String, String> reactions = getReactionsMap(posts, user);
        Map<String, List<String>> tagsMap = getTagsMap(posts);
        Map<String, PostStats> statsMap = getPostStatsMap(posts);
        PostMediaMaps mediaMaps = getPostMediaMaps(posts);
        List<PostResponse> responses = posts.stream()
                .map(post -> toPostResponse(post, reactions, tagsMap, statsMap, mediaMaps))
                .collect(Collectors.toList());

        String nextCursor = normalizeCursor(recommendationResponse.nextCursor());
        return new CursorPagedResponse<>(responses, size, nextCursor, nextCursor != null);
    }

    private CursorPagedResponse<PostResponse> getPostsByGroupIdFallback(
            String groupId,
            int size,
            String cursor,
            User user) {
        PostCursor decodedCursor = decodePostCursor(cursor);
        int limitPlusOne = size + 1;
        Pageable limit = PageRequest.of(0, limitPlusOne);

        List<Post> candidatePosts = decodedCursor == null
                ? postRepository.findLatestPostsByGroupId(
                        groupId,
                        ContentStatus.ACTIVE,
                        UserStatus.ACTIVE,
                        limit)
                : postRepository.findLatestPostsByGroupIdAfter(
                        groupId,
                        ContentStatus.ACTIVE,
                        UserStatus.ACTIVE,
                        decodedCursor.createdAt(),
                        decodedCursor.postId(),
                        limit);

        boolean hasNext = candidatePosts.size() > size;
        List<Post> posts = hasNext
                ? candidatePosts.subList(0, size)
                : candidatePosts;

        Map<String, String> reactions = getReactionsMap(posts, user);
        Map<String, List<String>> tagsMap = getTagsMap(posts);
        Map<String, PostStats> statsMap = getPostStatsMap(posts);
        PostMediaMaps mediaMaps = getPostMediaMaps(posts);
        List<PostResponse> responses = posts.stream()
                .map(post -> toPostResponse(post, reactions, tagsMap, statsMap, mediaMaps))
                .collect(Collectors.toList());

        String nextCursor = hasNext && !posts.isEmpty()
                ? GROUP_FALLBACK_CURSOR_PREFIX + encodePostCursor(posts.getLast())
                : null;
        return new CursorPagedResponse<>(responses, size, nextCursor, nextCursor != null);
    }

    private Map<String, String> getReactionsMap(List<Post> posts, User user) {
        if (user == null || posts.isEmpty()) {
            return Map.of();
        }
        return postReactionRepository.findByPostInAndUser(posts, user).stream()
                .collect(Collectors.toMap(r -> r.getPost().getId(), r -> r.getType().name()));
    }

    public List<String> getTagsForPost(String postId) {
        return postTagRepository.findTagNamesByPostId(postId);
    }

    private Map<String, List<String>> getTagsMap(List<Post> posts) {
        if (posts.isEmpty())
            return Collections.emptyMap();
        List<String> postIds = new ArrayList<>();
        for (Post post : posts) {
            postIds.add(post.getId());
            if (post.getSharedPost() != null) {
                postIds.add(post.getSharedPost().getId());
            }
        }
        List<PostTag> postTags = postTagRepository.findByIdPostIdIn(postIds);
        Map<String, List<String>> tagsMap = new HashMap<>();
        for (PostTag postTag : postTags) {
            tagsMap.computeIfAbsent(postTag.getId().getPostId(), ignored -> new ArrayList<>())
                    .add(postTag.getId().getTagName());
        }
        return tagsMap;
    }

    private Map<String, PostStats> getPostStatsMap(List<Post> posts) {
        if (posts.isEmpty()) {
            return Map.of();
        }

        List<String> postIds = posts.stream()
                .map(Post::getId)
                .toList();

        Map<String, int[]> counts = new HashMap<>();
        for (String postId : postIds) {
            counts.put(postId, new int[4]);
        }

        // Like & Dislike counts
        for (PostReactionCountProjection projection : postReactionRepository.countByPostIdsGroupedByType(postIds)) {
            int[] postCounts = counts.get(projection.getPostId());
            if (postCounts == null) {
                continue;
            }
            if (projection.getType() == ReactionType.LIKE) {
                postCounts[0] = projection.getCount().intValue();
            } else if (projection.getType() == ReactionType.DISLIKE) {
                postCounts[1] = projection.getCount().intValue();
            }
        }

        // Comment counts
        for (PostCountProjection projection : commentRepository.countByPostIdsAndStatus(postIds,
                ContentStatus.ACTIVE)) {
            int[] postCounts = counts.get(projection.getPostId());
            if (postCounts != null) {
                postCounts[2] = projection.getCount().intValue();
            }
        }

        // Share counts
        for (PostCountProjection projection : postRepository.countSharesByPostIdsAndStatus(postIds,
                ContentStatus.ACTIVE)) {
            int[] postCounts = counts.get(projection.getPostId());
            if (postCounts != null) {
                postCounts[3] = projection.getCount().intValue();
            }
        }

        return counts.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            int[] value = entry.getValue();
                            return new PostStats(value[0], value[1], value[2], value[3]);
                        }));
    }

    private PostResponse toPostResponse(Post post, Map<String, String> reactions,
            Map<String, List<String>> tagsMap, Map<String, PostStats> statsMap) {
        List<String> tags = tagsMap.getOrDefault(post.getId(), Collections.emptyList());
        List<String> sharedTags = post.getSharedPost() != null
                ? tagsMap.getOrDefault(post.getSharedPost().getId(), Collections.emptyList())
                : null;
        return PostResponse.fromPost(
                post,
                reactions.get(post.getId()),
                tags,
                sharedTags,
                post.getGroup() != null ? post.getGroup().getName() : null,
                post.getSharedPost() != null && post.getSharedPost().getGroup() != null
                        ? post.getSharedPost().getGroup().getName()
                        : null,
                statsMap.getOrDefault(post.getId(), PostStats.empty()));
    }

    private PostResponse toPostResponse(Post post, Map<String, String> reactions,
            Map<String, List<String>> tagsMap, Map<String, PostStats> statsMap,
            PostMediaMaps mediaMaps) {
        PostResponse response = toPostResponse(post, reactions, tagsMap, statsMap);
        response.setImages(mediaMaps.images().getOrDefault(post.getId(), List.of()));
        response.setVideos(mediaMaps.videos().getOrDefault(post.getId(), List.of()));

        Post sharedPost = post.getSharedPost();
        if (sharedPost != null && response.getSharedPost() != null) {
            response.getSharedPost().setImages(sharedPost.getStatus() == ContentStatus.ACTIVE
                    ? mediaMaps.images().getOrDefault(sharedPost.getId(), List.of())
                    : List.of());
            response.getSharedPost().setVideos(
                    mediaMaps.videos().getOrDefault(sharedPost.getId(), List.of()));
        }
        return response;
    }

    private PostMediaMaps getPostMediaMaps(List<Post> posts) {
        if (posts.isEmpty()) {
            return PostMediaMaps.empty();
        }

        LinkedHashSet<String> postIds = new LinkedHashSet<>();
        for (Post post : posts) {
            postIds.add(post.getId());
            if (post.getSharedPost() != null) {
                postIds.add(post.getSharedPost().getId());
            }
        }

        List<String> ids = List.copyOf(postIds);
        return new PostMediaMaps(
                groupMediaByPostId(postRepository.findImageUrlsByPostIds(ids)),
                groupMediaByPostId(postRepository.findVideoUrlsByPostIds(ids)));
    }

    private Map<String, List<String>> groupMediaByPostId(List<PostMediaProjection> media) {
        Map<String, List<String>> mediaByPostId = new HashMap<>();
        for (PostMediaProjection item : media) {
            if (item.getPostId() == null || item.getUrl() == null) {
                continue;
            }
            mediaByPostId.computeIfAbsent(item.getPostId(), ignored -> new ArrayList<>())
                    .add(item.getUrl());
        }
        return mediaByPostId;
    }

    private record PostMediaMaps(
            Map<String, List<String>> images,
            Map<String, List<String>> videos) {
        private static PostMediaMaps empty() {
            return new PostMediaMaps(Map.of(), Map.of());
        }
    }

    public CursorPagedResponse<PostResponse> getPostsByTopicName(
            User user,
            String topicName,
            int size,
            String cursor) throws Exception {
        validatePostFeedPageSize(size);

        if (isTopicFallbackCursor(cursor)) {
            return getPostsByTopicNameFallback(
                    user,
                    topicName,
                    size,
                    fallbackCursorValue(cursor, TOPIC_FALLBACK_CURSOR_PREFIX));
        }

        try {
            return getRecommendedPostsByTopicName(user, topicName, size, cursor);
        } catch (RecommendationClientException e) {
            log.warn("Topic recommendation service is unavailable; using the MySQL topic fallback: {}", e.getMessage());
            return getPostsByTopicNameFallback(user, topicName, size, null);
        }
    }

    private CursorPagedResponse<PostResponse> getRecommendedPostsByTopicName(
            User user,
            String topicName,
            int size,
            String cursor) {
        RecommendationFeedResponse recommendationResponse = recommendationClient
                .getTopicRecommendations(user.getId(), topicName, size, cursor);

        List<String> recommendedPostIds = recommendationResponse.items().stream()
                .map(RecommendationItem::postId)
                .filter(postId -> postId != null && !postId.isBlank())
                .toList();

        Map<String, Post> postsById = recommendedPostIds.isEmpty()
                ? Map.of()
                : postRepository.findVisiblePostsByIdsAndTopicName(
                        recommendedPostIds,
                        topicName,
                        ContentStatus.ACTIVE,
                        UserStatus.ACTIVE).stream()
                        .collect(Collectors.toMap(Post::getId, Function.identity()));

        // The Python service already determined the ranking order.
        List<Post> posts = recommendedPostIds.stream()
                .map(postsById::get)
                .filter(java.util.Objects::nonNull)
                .toList();

        Map<String, String> reactions = getReactionsMap(posts, user);
        Map<String, List<String>> tagsMap = getTagsMap(posts);
        Map<String, PostStats> statsMap = getPostStatsMap(posts);
        PostMediaMaps mediaMaps = getPostMediaMaps(posts);
        List<PostResponse> responses = posts.stream()
                .map(post -> toPostResponse(post, reactions, tagsMap, statsMap, mediaMaps))
                .collect(Collectors.toList());

        String nextCursor = normalizeCursor(recommendationResponse.nextCursor());
        return new CursorPagedResponse<>(responses, size, nextCursor, nextCursor != null);
    }

    private CursorPagedResponse<PostResponse> getPostsByTopicNameFallback(
            User user,
            String topicName,
            int size,
            String cursor) {
        PostCursor decodedCursor = decodePostCursor(cursor);
        int limitPlusOne = size + 1;

        Pageable limit = PageRequest.of(0, limitPlusOne);
        List<Post> candidatePosts = decodedCursor == null
                ? postRepository.findLatestPostsByTopicName(
                        topicName,
                        ContentStatus.ACTIVE,
                        UserStatus.ACTIVE,
                        limit)
                : postRepository.findLatestPostsByTopicNameAfter(
                        topicName,
                        ContentStatus.ACTIVE,
                        UserStatus.ACTIVE,
                        decodedCursor.createdAt(),
                        decodedCursor.postId(),
                        limit);

        boolean hasNext = candidatePosts.size() > size;
        List<Post> posts = hasNext
                ? candidatePosts.subList(0, size)
                : candidatePosts;

        Map<String, String> reactions = getReactionsMap(posts, user);
        Map<String, List<String>> tagsMap = getTagsMap(posts);
        Map<String, PostStats> statsMap = getPostStatsMap(posts);
        PostMediaMaps mediaMaps = getPostMediaMaps(posts);
        List<PostResponse> responses = posts.stream()
                .map(post -> toPostResponse(post, reactions, tagsMap, statsMap, mediaMaps))
                .collect(Collectors.toList());

        String nextCursor = hasNext && !posts.isEmpty()
                ? TOPIC_FALLBACK_CURSOR_PREFIX + encodePostCursor(posts.getLast())
                : null;
        return new CursorPagedResponse<>(responses, size, nextCursor, nextCursor != null);
    }

    // --- ADMIN ---

    public Page<AdminPostResponse> getPostsByUserId(String userId, Pageable pageable) throws Exception {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        Page<Post> posts = postRepository.findByUser(user, pageable);
        Map<String, List<String>> tagsMap = getTagsMap(posts.getContent());
        Map<String, PostStats> statsMap = getPostStatsMap(posts.getContent());
        return posts.map(post -> {
            List<String> tags = tagsMap.getOrDefault(post.getId(), Collections.emptyList());
            List<String> sharedTags = post.getSharedPost() != null
                    ? tagsMap.getOrDefault(post.getSharedPost().getId(), Collections.emptyList())
                    : null;
            return AdminPostResponse.fromEntity(
                    post,
                    tags,
                    sharedTags,
                    statsMap.getOrDefault(post.getId(), PostStats.empty()));
        });
    }

    public Page<AdminPostResponse> searchPosts(String query, String status, Pageable pageable) throws Exception {
        if (query != null && query.trim().isEmpty()) {
            query = null;
        }
        ContentStatus contentStatus = parseAndValidateContentStatus(status);
        Page<Post> posts = postRepository.searchPosts(query, contentStatus, pageable);
        Map<String, List<String>> tagsMap = getTagsMap(posts.getContent());
        Map<String, PostStats> statsMap = getPostStatsMap(posts.getContent());

        return posts.map(post -> {
            List<String> tags = tagsMap.getOrDefault(post.getId(), Collections.emptyList());
            List<String> sharedTags = post.getSharedPost() != null
                    ? tagsMap.getOrDefault(post.getSharedPost().getId(), Collections.emptyList())
                    : null;
            return AdminPostResponse.fromEntity(
                    post,
                    tags,
                    sharedTags,
                    statsMap.getOrDefault(post.getId(), PostStats.empty()));
        });
    }

    public AdminPostResponse getAdminPostResponseById(String postId) throws Exception {
        Post post = getPostById(postId);
        List<String> tags = getTagsForPost(postId);
        List<String> sharedTags = post.getSharedPost() != null
                ? getTagsForPost(post.getSharedPost().getId())
                : null;
        PostStats stats = getPostStatsMap(List.of(post))
                .getOrDefault(post.getId(), PostStats.empty());
        return AdminPostResponse.fromEntity(post, tags, sharedTags, stats);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void adminActivePost(String postId) throws Exception {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài viết"));

        post.setStatus(ContentStatus.ACTIVE);
        postRepository.saveAndFlush(post);

        try {
            long updatedPostCount = postNeo4jRepository.updatePostStatus(postId, ContentStatus.ACTIVE.name());
            if (updatedPostCount != 1) {
                throw new IllegalStateException("Neo4j did not restore the post status");
            }
        } catch (Exception e) {
            try {
                postNeo4jRepository.updatePostStatus(postId, ContentStatus.DELETED.name());
            } catch (Exception compensationException) {
                e.addSuppressed(compensationException);
            }
            throw new RuntimeException("Lỗi khôi phục trạng thái bài viết trên Neo4j", e);
        }
    }
}
