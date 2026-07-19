package com.example.campushub.services;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.campushub.dtos.record.posts.PostCreatedPayload;
import com.example.campushub.dtos.record.posts.PostStatusChangedPayload;
import com.example.campushub.dtos.record.posts.PostSharedPayload;
import com.example.campushub.dtos.record.posts.PostStats;
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
import com.example.campushub.exceptions.ResourceNotFoundException;
import com.example.campushub.exceptions.ForbiddenException;
import com.example.campushub.exceptions.BadRequestException;
import com.example.campushub.models.jpa.Group;
import com.example.campushub.models.jpa.GroupMember;
import com.example.campushub.models.jpa.GroupMemberId;
import com.example.campushub.models.jpa.Neo4jSyncEvent;
import com.example.campushub.models.jpa.Post;
import com.example.campushub.models.jpa.PostReaction;
import com.example.campushub.models.jpa.PostReactionId;
import com.example.campushub.models.jpa.PostTag;
import com.example.campushub.models.jpa.PostTagId;
import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.CommentRepository;
import com.example.campushub.repositories.jpa.GroupMemberRepository;
import com.example.campushub.repositories.jpa.GroupRepository;
import com.example.campushub.repositories.jpa.Neo4jSyncEventRepository;
import com.example.campushub.repositories.jpa.PostReactionRepository;
import com.example.campushub.repositories.jpa.PostRepository;
import com.example.campushub.repositories.jpa.PostTagRepository;
import com.example.campushub.repositories.jpa.UserRepository;
import com.example.campushub.repositories.jpa.projections.PostCountProjection;
import com.example.campushub.repositories.jpa.projections.PostReactionCountProjection;
import com.example.campushub.repositories.neo4j.InterestNeo4jRepository;
import com.example.campushub.repositories.neo4j.PostCursorProjection;
import com.example.campushub.repositories.neo4j.PostNeo4jRepository;
import com.example.campushub.repositories.neo4j.UserNeo4jRepository;
import com.example.campushub.responses.CursorPagedResponse;
import com.example.campushub.responses.PostResponse;
import com.example.campushub.responses.admin.AdminPostResponse;

import lombok.RequiredArgsConstructor;
import net.datafaker.Faker;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PostService {
    private static final int MAX_POST_FEED_PAGE_SIZE = 50;
    private static final String POST_CURSOR_SEPARATOR = "|";

    private final PostRepository postRepository;
    private final PostNeo4jRepository postNeo4jRepository;
    private final PostTagRepository postTagRepository;
    private final InterestNeo4jRepository tagNeo4jRepository;
    private final UserNeo4jRepository userNeo4jRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final PostReactionRepository postReactionRepository;
    private final CommentRepository commentRepository;
    private final Neo4jSyncEventRepository neo4jSyncEventRepository;
    private final FileUploadService fileUploadService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final Faker faker;

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
    public void createPost(User user, CreatePostDTO dto, List<MultipartFile> images) throws Exception {
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

        if (images != null && !images.isEmpty()) {
            List<String> imageUrls = fileUploadService.uploadFiles(images, "posts");
            post.setImages(imageUrls);
        }

        postRepository.save(post);

        List<PostTag> postTags = dto.getTags().stream()
                .map(tagName -> PostTag.builder()
                        .id(new PostTagId(post.getId(), tagName))
                        .post(post)
                        .build())
                .toList();
        postTagRepository.saveAll(postTags);

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

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int seedPosts(User currentUser, int count, int maxReactions, boolean includeImages, boolean includeGroups) {
        if (count < 1) {
            throw new BadRequestException("count must be greater than 0");
        }

        int limitedCount = Math.min(count, 100);
        int limitedMaxReactions = Math.max(0, Math.min(maxReactions, 50));
        Set<String> validTags = new LinkedHashSet<>(tagNeo4jRepository.findLeafTagsToList());
        if (validTags.isEmpty()) {
            throw new BadRequestException("Cannot seed posts because no interest tags exist");
        }

        List<User> users = userRepository.findAll().stream()
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .filter(user -> hasSeedableInterests(user, validTags))
                .collect(Collectors.toCollection(ArrayList::new));
        if (currentUser != null
                && currentUser.getStatus() == UserStatus.ACTIVE
                && hasSeedableInterests(currentUser, validTags)
                && users.stream().noneMatch(user -> user.getId().equals(currentUser.getId()))) {
            users.add(currentUser);
        }
        if (users.isEmpty()) {
            throw new BadRequestException("Cannot seed posts because no active users with valid interests exist");
        }

        int successCount = 0;
        for (int i = 0; i < limitedCount; i++) {
            Post post = null;
            User author = randomElement(users);
            List<String> authorInterests = findValidUserInterestNames(author.getId(), validTags);
            if (authorInterests.isEmpty()) {
                continue;
            }
            Set<String> postTags = pickRandomTags(authorInterests, 1, 3);
            Group group = includeGroups ? pickRandomApprovedGroup(author) : null;
            String seed = faker.internet().uuid();

            post = Post.builder()
                    .content(buildSeedPostContent(postTags))
                    .user(author)
                    .group(group)
                    .build();
            post.setCreatedAt(randomCreatedAtWithinLastDays(3));

            if (includeImages && ThreadLocalRandom.current().nextInt(100) < 40) {
                post.setImages(List.of("https://picsum.photos/seed/" + seed + "/900/600"));
            }

            post = postRepository.save(post);

            Post savedPost = post;
            List<PostTag> postTagsMain = postTags.stream()
                    .map(tagName -> PostTag.builder()
                            .id(new PostTagId(savedPost.getId(), tagName))
                            .post(savedPost)
                            .build())
                    .toList();
            postTagRepository.saveAll(postTagsMain);

            seedPostReactions(post, users, limitedMaxReactions);

            PostCreatedPayload payload = new PostCreatedPayload(
                    savedPost.getId(),
                    author.getId(),
                    group != null ? group.getId() : null,
                    postTags,
                    savedPost.getCreatedAt());

            neo4jSyncEventRepository.save(Neo4jSyncEvent.pending(
                    Neo4jEventType.POST_CREATED,
                    savedPost.getId(),
                    toJson(payload)));
            successCount++;
        }
        return successCount;
    }

    private boolean hasSeedableInterests(User user, Set<String> validTags) {
        return !findValidUserInterestNames(user.getId(), validTags).isEmpty();
    }

    private List<String> findValidUserInterestNames(String userId, Set<String> validTags) {
        Set<String> interests = findUserInterestNames(userId);
        if (interests.isEmpty()) {
            return Collections.emptyList();
        }
        return interests.stream()
                .filter(validTags::contains)
                .collect(Collectors.toList());
    }

    private void seedPostReactions(Post post, List<User> users, int maxReactions) {
        if (maxReactions == 0 || users.isEmpty()) {
            return;
        }

        List<User> candidates = new ArrayList<>(users);
        Collections.shuffle(candidates);
        int reactionCount = ThreadLocalRandom.current().nextInt(0, Math.min(maxReactions, candidates.size()) + 1);
        if (reactionCount == 0) {
            return;
        }

        List<PostReaction> reactions = new ArrayList<>();
        for (User reactor : candidates.subList(0, reactionCount)) {
            ReactionType type = ThreadLocalRandom.current().nextInt(100) < 80
                    ? ReactionType.LIKE
                    : ReactionType.DISLIKE;
            reactions.add(PostReaction.builder()
                    .id(new PostReactionId(post.getId(), reactor.getId()))
                    .post(post)
                    .user(reactor)
                    .type(type)
                    .build());
        }

        postReactionRepository.saveAll(reactions);
    }

    private Group pickRandomApprovedGroup(User author) {
        List<Group> groups = groupMemberRepository.findByUser(author).stream()
                .filter(member -> member.getStatus() == MemberStatus.APPROVED)
                .map(GroupMember::getGroup)
                .filter(group -> group.getStatus() == GroupStatus.ACTIVE)
                .collect(Collectors.toList());
        if (groups.isEmpty() || ThreadLocalRandom.current().nextInt(100) >= 10) {
            return null;
        }
        return randomElement(groups);
    }

    private Set<String> pickRandomTags(List<String> tags, int min, int max) {
        List<String> shuffledTags = new ArrayList<>(tags);
        Collections.shuffle(shuffledTags);
        int upperBound = Math.min(max, shuffledTags.size());
        int lowerBound = Math.min(min, upperBound);
        int tagCount = ThreadLocalRandom.current().nextInt(lowerBound, upperBound + 1);
        return new LinkedHashSet<>(shuffledTags.subList(0, tagCount));
    }

    private String buildSeedPostContent(Set<String> tags) {
        List<String> topicList = tags.isEmpty() ? List.of("CampusHub") : new ArrayList<>(tags);
        String primaryTopic = topicList.get(0);
        String topicText = joinTopics(topicList);

        List<String> openings = List.of(
                "Minh dang tim cach hoc %s hieu qua hon trong hoc ky nay.",
                "Vua roi minh co thu ap dung %s vao mot bai tap nho va thay kha thu vi.",
                "Co ai trong CampusHub dang quan tam den %s khong?",
                "Minh dang gom tai lieu va kinh nghiem lien quan den %s.",
                "Sau mot buoi trao doi voi ban be, minh thay %s co nhieu diem dang de dao sau.");

        List<String> details = List.of(
                "Phan kho nhat hien tai la biet bat dau tu dau, nen minh muon nghe cach moi nguoi chia nho noi dung va luyen tap moi ngay.",
                "Minh thay hoc theo vi du thuc te de nho hon ly thuyet thuan tuy, nhung van can mot lo trinh ro rang de khong bi lan man.",
                "Neu co checklist, repo mau, slide mon hoc hoac bai viet hay thi moi nguoi de lai giup minh voi.",
                "Minh muon thu lam mot mini project trong 1-2 tuan de vua hoc vua co san pham dua vao portfolio.",
                "Chu de nay co ve hop de lap nhom hoc nho, moi nguoi co the cung dat muc tieu va review tien do hang tuan.");

        List<String> questions = List.of(
                "Moi nguoi thuong dung nguon nao de hoc %s?",
                "Neu bat dau lai tu dau voi %s, ban se hoc theo thu tu nao?",
                "Co loi sai nao khi hoc %s ma nguoi moi nen tranh khong?",
                "Ai co kinh nghiem lam project ve %s thi chia se giup minh vai tip nhe.",
                "Theo moi nguoi, nen hoc %s mot minh hay lap nhom hoc se hieu qua hon?");

        List<String> callsToAction = List.of(
                "Ban nao cung muc tieu thi comment de minh tao mot thread tong hop tai lieu.",
                "Neu du nguoi quan tam, minh se lap lich hoc chung va chia topic theo tung buoi.",
                "Minh se cap nhat lai nhung nguon huu ich nhat sau khi tong hop y kien cua moi nguoi.",
                "Ai co goc nhin khac thi cu chia se, minh muon bai nay thanh noi gom kinh nghiem that su dung duoc.",
                "Cam on moi nguoi truoc, nhat la cac ban da tung hoc qua chu de nay.");

        String opening = String.format(randomElement(openings), topicText);
        String question = String.format(randomElement(questions), primaryTopic);
        return opening + " " + randomElement(details) + " " + question + " " + randomElement(callsToAction);
    }

    private String joinTopics(List<String> topics) {
        if (topics.size() == 1) {
            return topics.get(0);
        }
        if (topics.size() == 2) {
            return topics.get(0) + " va " + topics.get(1);
        }
        return topics.get(0) + ", " + topics.get(1) + " va " + topics.get(2);
    }

    private <T> T randomElement(List<T> values) {
        return values.get(ThreadLocalRandom.current().nextInt(values.size()));
    }

    private LocalDateTime randomCreatedAtWithinLastDays(int days) {
        long maxSecondsAgo = Duration.ofDays(days).toSeconds();
        long secondsAgo = ThreadLocalRandom.current().nextLong(maxSecondsAgo + 1);
        return LocalDateTime.now().minusSeconds(secondsAgo);
    }

    @Transactional("transactionManager")
    public void likePost(User user, String postId) throws Exception {
        Post post = getPostById(postId);
        PostReaction reaction = postReactionRepository.findByPostAndUser(post, user);

        boolean isNewLike = false;

        if (reaction == null) {
            reaction = PostReaction.builder()
                    .id(new PostReactionId(post.getId(), user.getId()))
                    .post(post)
                    .user(user)
                    .type(ReactionType.LIKE)
                    .build();
            postReactionRepository.save(reaction);
            isNewLike = true;
        } else if (reaction.getType() == ReactionType.DISLIKE) {
            reaction.setType(ReactionType.LIKE);
            postReactionRepository.save(reaction);
            isNewLike = true;
        } else {
            postReactionRepository.delete(reaction);
        }

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

        if (reaction == null) {
            reaction = PostReaction.builder()
                    .id(new PostReactionId(post.getId(), user.getId()))
                    .post(post)
                    .user(user)
                    .type(ReactionType.DISLIKE)
                    .build();
            postReactionRepository.save(reaction);
        } else if (reaction.getType() == ReactionType.LIKE) {
            reaction.setType(ReactionType.DISLIKE);
            postReactionRepository.save(reaction);
        } else {
            postReactionRepository.delete(reaction);
        }
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

        post.setStatus(ContentStatus.DELETED);

        postRepository.save(post);

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
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));
        validatePostFeedPageSize(size);
        PostCursor decodedCursor = decodePostCursor(cursor);
        int limitPlusOne = size + 1;
        Pageable limit = PageRequest.of(0, limitPlusOne);

        List<Post> candidatePosts = decodedCursor == null
                ? postRepository.findLatestPostsByUserId(
                        userId,
                        ContentStatus.ACTIVE,
                        limit)
                : postRepository.findLatestPostsByUserIdAfter(
                        userId,
                        ContentStatus.ACTIVE,
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
        List<PostResponse> responses = posts.stream()
                .map(post -> toPostResponse(post, reactions, tagsMap, statsMap))
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
        PostCursor decodedCursor = decodePostCursor(cursor);
        int limitPlusOne = size + 1;

        List<PostCursorProjection> candidatePosts = decodedCursor == null
                ? postNeo4jRepository.findLatestHomeFeedPosts(user.getId(), limitPlusOne)
                : postNeo4jRepository.findLatestHomeFeedPostsAfter(
                        user.getId(),
                        decodedCursor.createdAt(),
                        decodedCursor.postId(),
                        limitPlusOne);

        boolean hasNext = candidatePosts.size() > size;
        List<PostCursorProjection> pageCandidates = hasNext
                ? candidatePosts.subList(0, size)
                : candidatePosts;
        List<String> pagePostIds = pageCandidates.stream()
                .map(PostCursorProjection::getId)
                .collect(Collectors.toList());
        List<Post> posts = findActivePostsInOrder(pagePostIds);

        Map<String, String> reactions = getReactionsMap(posts, user);
        Map<String, List<String>> tagsMap = getTagsMap(posts);
        Map<String, PostStats> statsMap = getPostStatsMap(posts);
        List<PostResponse> responses = posts.stream()
                .map(post -> toPostResponse(post, reactions, tagsMap, statsMap))
                .collect(Collectors.toList());

        String nextCursor = hasNext && !pageCandidates.isEmpty()
                ? encodePostCursor(pageCandidates.getLast())
                : null;
        return new CursorPagedResponse<>(responses, size, nextCursor, nextCursor != null);
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

    private String encodePostCursor(PostCursorProjection post) {
        return encodePostCursor(post.getCreatedAt(), post.getId());
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

    private List<Post> findActivePostsInOrder(List<String> postIds) {
        if (postIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Post> posts = new ArrayList<>(
                postRepository.findByIdInAndStatus(postIds, ContentStatus.ACTIVE));
        posts.sort(Comparator.comparingInt(post -> postIds.indexOf(post.getId())));
        return posts;
    }

    private Set<String> findUserInterestNames(String userId) {
        if (userId == null || userId.isBlank()) {
            return Set.of();
        }
        Set<String> interests = userNeo4jRepository.findUserInterestNames(userId);
        return interests == null ? Set.of() : interests;
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
        PostCursor decodedCursor = decodePostCursor(cursor);
        int limitPlusOne = size + 1;
        Pageable limit = PageRequest.of(0, limitPlusOne);

        List<Post> candidatePosts = decodedCursor == null
                ? postRepository.findLatestPostsByGroupId(
                        groupId,
                        ContentStatus.ACTIVE,
                        limit)
                : postRepository.findLatestPostsByGroupIdAfter(
                        groupId,
                        ContentStatus.ACTIVE,
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
        List<PostResponse> responses = posts.stream()
                .map(post -> toPostResponse(post, reactions, tagsMap, statsMap))
                .collect(Collectors.toList());

        String nextCursor = hasNext && !posts.isEmpty()
                ? encodePostCursor(posts.getLast())
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
        return postNeo4jRepository.getTagNamesByPostId(postId);
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

    public CursorPagedResponse<PostResponse> getPostsByTopicName(
            User user,
            String topicName,
            int size,
            String cursor) throws Exception {
        validatePostFeedPageSize(size);
        PostCursor decodedCursor = decodePostCursor(cursor);
        int limitPlusOne = size + 1;

        List<PostCursorProjection> candidatePosts = decodedCursor == null
                ? postNeo4jRepository.findLatestPostsByTagName(topicName, limitPlusOne)
                : postNeo4jRepository.findLatestPostsByTagNameAfter(
                        topicName,
                        decodedCursor.createdAt(),
                        decodedCursor.postId(),
                        limitPlusOne);

        boolean hasNext = candidatePosts.size() > size;
        List<PostCursorProjection> pageCandidates = hasNext
                ? candidatePosts.subList(0, size)
                : candidatePosts;
        List<String> pagePostIds = pageCandidates.stream()
                .map(PostCursorProjection::getId)
                .collect(Collectors.toList());
        List<Post> posts = findActivePostsInOrder(pagePostIds);

        Map<String, String> reactions = getReactionsMap(posts, user);
        Map<String, List<String>> tagsMap = getTagsMap(posts);
        Map<String, PostStats> statsMap = getPostStatsMap(posts);
        List<PostResponse> responses = posts.stream()
                .map(post -> toPostResponse(post, reactions, tagsMap, statsMap))
                .collect(Collectors.toList());

        String nextCursor = hasNext && !pageCandidates.isEmpty()
                ? encodePostCursor(pageCandidates.getLast())
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
