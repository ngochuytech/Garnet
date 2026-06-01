package com.example.campushub.services;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.campushub.dtos.record.PostTagsDTO;
import com.example.campushub.dtos.users.CreatePostDTO;
import com.example.campushub.dtos.users.CreateSharePostDTO;
import com.example.campushub.dtos.users.UpdatePostDTO;
import com.example.campushub.enums.ContentStatus;
import com.example.campushub.enums.GroupStatus;
import com.example.campushub.enums.NotificationType;
import com.example.campushub.enums.ReactionType;
import com.example.campushub.enums.UserStatus;
import com.example.campushub.events.NotificationEvent;
import com.example.campushub.exceptions.DataNotFoundException;
import com.example.campushub.exceptions.ForbiddenAccessException;
import com.example.campushub.exceptions.InvalidContentStateException;
import com.example.campushub.exceptions.InvalidParamException;
import com.example.campushub.models.jpa.Post;
import com.example.campushub.models.jpa.PostEditHistory;
import com.example.campushub.models.jpa.PostReaction;
import com.example.campushub.models.jpa.PostReactionId;
import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.PostEditHistoryRepository;
import com.example.campushub.repositories.jpa.PostReactionRepository;
import com.example.campushub.repositories.jpa.PostRepository;
import com.example.campushub.repositories.jpa.UserRepository;
import com.example.campushub.repositories.jpa.GroupMemberRepository;
import com.example.campushub.models.jpa.GroupMember;
import com.example.campushub.models.jpa.GroupMemberId;
import com.example.campushub.models.jpa.Group;
import com.example.campushub.enums.MemberRole;
import com.example.campushub.enums.MemberStatus;
import com.example.campushub.repositories.jpa.GroupRepository;
import com.example.campushub.repositories.neo4j.PostNeo4jRepository;
import com.example.campushub.repositories.neo4j.InterestNeo4jRepository;
import com.example.campushub.responses.PostResponse;
import com.example.campushub.responses.admin.AdminPostResponse;

import lombok.RequiredArgsConstructor;
import net.datafaker.Faker;

@Service
@RequiredArgsConstructor
public class PostService {
    private static final int HOME_CANDIDATE_LIMIT = 140;
    private static final int TOPIC_CANDIDATE_LIMIT = 140;
    private static final int GROUP_CANDIDATE_LIMIT = 140;
    private static final Duration HOME_FEED_CACHE_TTL = Duration.ofMinutes(5);

    private final PostRepository postRepository;
    private final PostNeo4jRepository postNeo4jRepository;
    private final InterestNeo4jRepository tagNeo4jRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final PostReactionRepository postReactionRepository;
    private final PostEditHistoryRepository postEditHistoryRepository;
    private final FileUploadService fileUploadService;
    private final ApplicationEventPublisher eventPublisher;
    private final Faker faker;
    private final StringRedisTemplate redisTemplate;

    private ContentStatus parseAndValidateContentStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ContentStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidContentStateException("Tham số trạng thái bài viết không hợp lệ: " + status);
        }
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void createPost(User user, CreatePostDTO dto, List<MultipartFile> images) throws Exception {
        long existingTagsCount = tagNeo4jRepository.countByNameIn(dto.getTags());
        if (existingTagsCount != dto.getTags().size()) {
            throw new DataNotFoundException("Một hoặc nhiều chủ đề không tồn tại");
        }

        Group groupNode = null;
        if (dto.getGroupId() != null) {
            GroupMemberId memberId = new GroupMemberId(dto.getGroupId(), user.getId());
            GroupMember member = groupMemberRepository.findById(memberId)
                    .orElseThrow(() -> new ForbiddenAccessException("Bạn không phải là thành viên của nhóm này"));
            if (member.getStatus() != MemberStatus.APPROVED) {
                throw new ForbiddenAccessException("Bạn chưa phải là thành viên thức của nhóm này");
            }
            groupNode = groupRepository.findById(dto.getGroupId())
                    .orElseThrow(() -> new DataNotFoundException("Không tìm thấy nhóm"));
            if (groupNode.getStatus() != GroupStatus.ACTIVE) {
                throw new ForbiddenAccessException("Nhóm này đã bị lưu trữ và không còn cho phép đăng bài mới");
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
        try {
            postNeo4jRepository.createPost(user.getId(), post.getId(), dto.getTags(), post.getCreatedAt());
            if (dto.getGroupId() != null) {
                postNeo4jRepository.linkPostToGroup(post.getId(), dto.getGroupId());
            }
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi tạo bài viết trên Neo4j: " + e.getMessage());
        }
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int  seedPosts(User currentUser, int count, int maxReactions, boolean includeImages, boolean includeGroups) {
        if (count < 1) {
            throw new InvalidParamException("count must be greater than 0");
        }

        int limitedCount = Math.min(count, 100);
        int limitedMaxReactions = Math.max(0, Math.min(maxReactions, 50));
        Set<String> validTags = new LinkedHashSet<>(tagNeo4jRepository.findLeafTagsToList());
        if (validTags.isEmpty()) {
            throw new InvalidParamException("Cannot seed posts because no interest tags exist");
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
            throw new InvalidParamException("Cannot seed posts because no active users with valid interests exist");
        }

        int successCount = 0;
        for (int i = 0; i < limitedCount; i++) {
            Post post = null;
            try {
                User author = randomElement(users);
                Set<String> postTags = pickRandomTags(author.getInterests().stream()
                        .filter(validTags::contains)
                        .collect(Collectors.toList()), 2, 6);
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
                postNeo4jRepository.createPost(author.getId(), post.getId(), postTags, post.getCreatedAt());
                if (group != null) {
                    postNeo4jRepository.linkPostToGroup(post.getId(), group.getId());
                }

                seedPostReactions(post, users, limitedMaxReactions);
                successCount++;
            } catch (Exception e) {
                if (post != null && post.getId() != null) {
                    postRepository.delete(post);
                }
            }
        }
        return successCount;
    }

    private boolean hasSeedableInterests(User user, Set<String> validTags) {
        return user.getInterests() != null
                && user.getInterests().stream().anyMatch(validTags::contains);
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

        int liked = 0;
        int disliked = 0;
        List<PostReaction> reactions = new ArrayList<>();
        for (User reactor : candidates.subList(0, reactionCount)) {
            ReactionType type = ThreadLocalRandom.current().nextInt(100) < 80
                    ? ReactionType.LIKE
                    : ReactionType.DISLIKE;
            if (type == ReactionType.LIKE) {
                liked++;
            } else {
                disliked++;
            }

            reactions.add(PostReaction.builder()
                    .id(new PostReactionId(post.getId(), reactor.getId()))
                    .post(post)
                    .user(reactor)
                    .type(type)
                    .build());
        }

        postReactionRepository.saveAll(reactions);
        post.setLiked(liked);
        post.setDisliked(disliked);
        postRepository.save(post);
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
            post.setLiked(post.getLiked() + 1);
            postRepository.save(post);
            postReactionRepository.save(reaction);
            isNewLike = true;
        } else if (reaction.getType() == ReactionType.DISLIKE) {
            post.setLiked(post.getLiked() + 1);
            post.setDisliked(post.getDisliked() - 1);
            postRepository.save(post);
            reaction.setType(ReactionType.LIKE);
            postReactionRepository.save(reaction);
            isNewLike = true;
        } else {
            post.setLiked(post.getLiked() - 1);
            postRepository.save(post);
            postReactionRepository.delete(reaction);
        }

        if(!user.getId().equals(post.getUser().getId()) && isNewLike) {
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
            post.setDisliked(post.getDisliked() + 1);
            postRepository.save(post);
            postReactionRepository.save(reaction);
        } else if (reaction.getType() == ReactionType.LIKE) {
            post.setLiked(post.getLiked() - 1);
            post.setDisliked(post.getDisliked() + 1);
            postRepository.save(post);
            reaction.setType(ReactionType.DISLIKE);
            postReactionRepository.save(reaction);
        } else {
            post.setDisliked(post.getDisliked() - 1);
            postRepository.save(post);
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
            throw new DataNotFoundException("Một hoặc nhiều chủ đề không tồn tại");
        }

        originalPost.setSharedCount(originalPost.getSharedCount() + 1);
        postRepository.save(originalPost);

        Post sharedPost = Post.builder()
                .content(dto.getContent())
                .user(user)
                .sharedPost(originalPost)
                .build();
        postRepository.save(sharedPost);
        try {
            postNeo4jRepository.createSharedPost(user.getId(), sharedPost.getId(), originalPost.getId(), dto.getTags(), sharedPost.getCreatedAt());
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi tạo bài viết trên Neo4j: " + e.getMessage());
        }

        if (!user.getId().equals(originalPost.getUser().getId())) {
            NotificationEvent event = NotificationEvent.builder()
                    .recipientId(originalPost.getUser().getId())
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
                .orElseThrow(() -> new DataNotFoundException("Bài viết không tồn tại hoặc đã bị xóa"));
    }

    public PostResponse getActivePostResponseById(String postId, User user) throws Exception {
        Post post = getActivePostById(postId);
        String userReaction = getUserReaction(post, user);
        List<String> tags = getTagsForPost(postId);
        List<String> sharedTags = post.getSharedPost() != null
                ? getTagsForPost(post.getSharedPost().getId())
                : null;
        return PostResponse.fromPost(
                post,
                userReaction,
                tags,
                sharedTags,
                post.getGroup() != null ? post.getGroup().getName() : null,
                post.getSharedPost() != null && post.getSharedPost().getGroup() != null ? post.getSharedPost().getGroup().getName() : null);
    }

    public Post getPostById(String postId) throws Exception {
        return postRepository.findById(postId)
                .orElseThrow(() -> new DataNotFoundException("Post not found"));
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
        return posts.stream()
                .map(post -> toPostResponse(post, reactions, tagsMap))
                .collect(Collectors.toList());
    }

    @Transactional("transactionManager")
    public void editPost(User user, String postId, UpdatePostDTO dto) throws Exception {
        Post post = getPostById(postId);
        if (!post.getUser().getId().equals(user.getId())) {
            throw new ForbiddenAccessException("Bạn không có quyền chỉnh sửa bài viết này");
        }

        if (post.getStatus() != ContentStatus.ACTIVE) {
            throw new InvalidContentStateException("Chỉ có thể chỉnh sửa bài viết đang ở trạng thái ACTIVE");
        }

        PostEditHistory history = PostEditHistory.builder()
                .post(post)
                .oldContent(post.getContent())
                .build();
        postEditHistoryRepository.save(history);

        post.setContent(dto.getContent());
        postRepository.save(post);
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
            throw new ForbiddenAccessException("Bạn không có quyền xóa bài viết này");
        }
        
        post.setStatus(ContentStatus.DELETED);

        Post sharedPost;
        if (post.getSharedPost() != null) {
            sharedPost = post.getSharedPost();
            sharedPost.setSharedCount(sharedPost.getSharedCount() - 1);
            postRepository.save(sharedPost);
        }
        postRepository.save(post);

        try {
            postNeo4jRepository.updatePostStatus(postId, ContentStatus.DELETED.name());
        } catch (Exception e) {
            throw new RuntimeException("Lỗi cập nhật trạng thái bài viết trên Neo4j: " + e.getMessage());
        }
    }

    public Page<PostResponse> getActivePostsByUserId(String userId, Pageable pageable, User currentUser)
            throws Exception {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DataNotFoundException("Người dùng không tồn tại"));
        Page<Post> posts = postRepository.findByUserAndStatus(user, ContentStatus.ACTIVE, pageable);
        Map<String, String> reactions = getReactionsMap(posts.getContent(), currentUser);
        Map<String, List<String>> tagsMap = getTagsMap(posts.getContent());
        return posts.map(post -> toPostResponse(post, reactions, tagsMap));
    }

    public Page<PostResponse> getPostsForHomeResponses(Pageable pageable, User user) throws Exception {
        String cacheKey = buildHomeFeedCacheKey(user);
        List<String> rankedPostIds = getCachedHomeFeedPostIds(cacheKey);
        if (rankedPostIds.isEmpty()) {
            rankedPostIds = buildHomeFeedRankedPostIds(user);
            cacheHomeFeedPostIds(cacheKey, rankedPostIds);
        }

        // Nếu ko có bài viết nào phù hợp, trả rỗng
        if (rankedPostIds.isEmpty()) {
            Page<Post> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
            return emptyPage.map(post -> null); 
        }

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), rankedPostIds.size());
        
        List<Post> pagedPostsList = Collections.emptyList();
        if (start < rankedPostIds.size()) {
            List<String> pagePostIds = rankedPostIds.subList(start, end);
            pagedPostsList = postRepository.findByIdInAndStatus(pagePostIds, ContentStatus.ACTIVE);
            pagedPostsList.sort(Comparator.comparingInt(post -> pagePostIds.indexOf(post.getId())));
        }

        Page<Post> posts = new PageImpl<>(pagedPostsList, pageable, rankedPostIds.size());

        Map<String, String> reactions = getReactionsMap(posts.getContent(), user);
        Map<String, List<String>> tagsMap = getTagsMap(posts.getContent());
        return posts.map(post -> toPostResponse(post, reactions, tagsMap));
    }

    private List<String> buildHomeFeedRankedPostIds(User user) {
        Set<String> interests = user.getInterests();
        // TH: người dùng ko có sở thích
        if (interests == null || interests.isEmpty()) {
            Pageable candidatePage = PageRequest.of(
                    0,
                    HOME_CANDIDATE_LIMIT,
                    Sort.by(Sort.Direction.DESC, "createdAt"));
            return postRepository.findByStatus(ContentStatus.ACTIVE, candidatePage).getContent().stream()
                    .map(Post::getId)
                    .collect(Collectors.toList());
        }

        // Lấy Post IDs từ Neo4j
        List<String> postIds = postNeo4jRepository.findActivePostIdsByTagNames(interests, HOME_CANDIDATE_LIMIT);
        if (postIds.isEmpty()) {
            return Collections.emptyList();
        }

        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        // Lấy thông tin Post từ MySQL để tính điểm
        List<Post> relevantPosts = postRepository.findActivePostsByIdsAndDateAfter(postIds, thirtyDaysAgo);

        relevantPosts.sort((p1, p2) -> {
            double score1 = calculateHackerNewsScore(p1);
            double score2 = calculateHackerNewsScore(p2);
            return Double.compare(score2, score1);
        });

        return relevantPosts.stream()
                .map(Post::getId)
                .collect(Collectors.toList());
    }

    private String buildHomeFeedCacheKey(User user) {
        Set<String> interests = user.getInterests();
        String interestFingerprint = "none";
        if (interests != null && !interests.isEmpty()) {
            interestFingerprint = Integer.toHexString(interests.stream()
                    .sorted()
                    .collect(Collectors.joining(","))
                    .hashCode());
        }
        return "campushub:home-feed:" + user.getId() + ":" + interestFingerprint;
    }

    private List<String> getCachedHomeFeedPostIds(String cacheKey) {
        try {
            List<String> cachedPostIds = redisTemplate.opsForList().range(cacheKey, 0, -1);
            return cachedPostIds == null ? Collections.emptyList() : cachedPostIds;
        } catch (DataAccessException e) {
            return Collections.emptyList();
        }
    }

    private void cacheHomeFeedPostIds(String cacheKey, List<String> postIds) {
        if (postIds.isEmpty()) {
            return;
        }
        try {
            redisTemplate.delete(cacheKey);
            redisTemplate.opsForList().rightPushAll(cacheKey, postIds);
            redisTemplate.expire(cacheKey, HOME_FEED_CACHE_TTL);
        } catch (DataAccessException e) {
            System.err.println("Failed to cache home feed post IDs: " + e.getMessage());
        }
    }

    private double calculateHackerNewsScore(Post post) {
        int likes = post.getLiked() != null ? post.getLiked() : 0;
        int dislikes = post.getDisliked() != null ? post.getDisliked() : 0;
        int comments = post.getCommentCount() != null ? post.getCommentCount() : 0;
        
        int E = likes + (comments * 3) - (dislikes * 2);
        
        long hoursBetween = java.time.temporal.ChronoUnit.HOURS.between(post.getCreatedAt(), LocalDateTime.now());
        double T = Math.max(hoursBetween, 0.0);
        double G = 1.8;
        
        return E / Math.pow(T + 2, G);
    }

    public Slice<PostResponse> getPostsByGroupId(String groupId, Pageable pageable, User user) throws Exception {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new DataNotFoundException("Nhóm không tồn tại hoặc đã bị xóa"));
        if (group.getStatus() == GroupStatus.DELETED) {
            throw new DataNotFoundException("Nhóm không tồn tại hoặc đã bị xóa");
        }
        Pageable candidatePage = PageRequest.of(
                0,
                GROUP_CANDIDATE_LIMIT,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Post> posts = new ArrayList<>(
                postRepository.findByGroupIdAndStatus(groupId, ContentStatus.ACTIVE, candidatePage).getContent());
                
        if(posts.isEmpty()) {
            return new SliceImpl<>(Collections.emptyList(), pageable, false);
        }

        if (posts.size() > 1) {
            posts.sort((p1, p2) -> {
                double score1 = calculateHackerNewsScore(p1);
                double score2 = calculateHackerNewsScore(p2);
                return Double.compare(score2, score1);
            });
        }

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), posts.size());
        boolean hasNext = end < posts.size();
        if (start >= posts.size()) {
            return new SliceImpl<>(Collections.emptyList(), pageable, false);
        }
        List<Post> pagedPosts = posts.subList(start, end);

        Map<String, String> reactions = getReactionsMap(pagedPosts, user);
        Map<String, List<String>> tagsMap = getTagsMap(pagedPosts);

        List<PostResponse> responses = pagedPosts.stream()
                .map(post -> toPostResponse(post, reactions, tagsMap))
                .collect(Collectors.toList());
        return new SliceImpl<>(responses, pageable, hasNext);
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
        List<PostTagsDTO> postNodes = postNeo4jRepository.findTagsByPostIds(postIds);
        return postNodes.stream()
                .collect(Collectors.toMap(
                        PostTagsDTO::getPostId,
                        proj -> {
                            Set<String> tags = proj.getTagNames();
                            return (tags == null || tags.isEmpty()) 
                                    ? Collections.<String>emptyList() 
                                    : new ArrayList<>(tags);
                        }));
    }

    private PostResponse toPostResponse(Post post, Map<String, String> reactions,
            Map<String, List<String>> tagsMap) {
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
                post.getSharedPost() != null && post.getSharedPost().getGroup() != null ? post.getSharedPost().getGroup().getName() : null);
    }

    public Slice<PostResponse> getPostsByTopicName(User user, String topicName, Pageable pageable) throws Exception {
        List<String> postIds = postNeo4jRepository.findActivePostIdsByTagName(topicName, TOPIC_CANDIDATE_LIMIT);
        if (postIds.isEmpty()) {
            return new SliceImpl<>(Collections.emptyList(), pageable, false);
        }

        List<Post> posts = postRepository.findByIdInAndStatus(postIds, ContentStatus.ACTIVE);
        posts.sort((p1, p2) -> {
            double score1 = calculateHackerNewsScore(p1);
            double score2 = calculateHackerNewsScore(p2);
            return Double.compare(score2, score1);
        });

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), posts.size());
        boolean hasNext = end < posts.size();
        if (start >= posts.size()) {
            return new SliceImpl<>(Collections.emptyList(), pageable, false);
        }
        List<Post> pagedPosts = posts.subList(start, end);

        Map<String, String> reactions = getReactionsMap(pagedPosts, user);
        Map<String, List<String>> tagsMap = getTagsMap(pagedPosts);

        List<PostResponse> responses = pagedPosts.stream()
                .map(post -> toPostResponse(post, reactions, tagsMap))
                .collect(Collectors.toList());
        return new SliceImpl<>(responses, pageable, hasNext);
    }

    // --- ADMIN ---

    public Page<AdminPostResponse> getPostsByUserId(String userId, Pageable pageable) throws Exception {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DataNotFoundException("Người dùng không tồn tại"));

        Page<Post> posts = postRepository.findByUser(user, pageable);
        Map<String, List<String>> tagsMap = getTagsMap(posts.getContent());
        return posts.map(post -> {
            List<String> tags = tagsMap.getOrDefault(post.getId(), Collections.emptyList());
            List<String> sharedTags = post.getSharedPost() != null
                    ? tagsMap.getOrDefault(post.getSharedPost().getId(), Collections.emptyList())
                    : null;
            return AdminPostResponse.fromEntity(post, tags, sharedTags);
        });
    }

    public Page<AdminPostResponse> searchPosts(String query, String status, Pageable pageable) throws Exception {
        if(query != null && query.trim().isEmpty()) {
            query = null;
        }
        ContentStatus contentStatus = parseAndValidateContentStatus(status);
        Page<Post> posts = postRepository.searchPosts(query, contentStatus, pageable);
        Map<String, List<String>> tagsMap = getTagsMap(posts.getContent());
        
        return posts.map(post -> {
            List<String> tags = tagsMap.getOrDefault(post.getId(), Collections.emptyList());
            List<String> sharedTags = post.getSharedPost() != null
                    ? tagsMap.getOrDefault(post.getSharedPost().getId(), Collections.emptyList())
                    : null;
            return AdminPostResponse.fromEntity(post, tags, sharedTags);
        });
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void adminActivePost(String postId) throws Exception {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new DataNotFoundException("Không tìm thấy bài viết"));

        post.setStatus(ContentStatus.ACTIVE);
        postRepository.save(post);

        try {
            postNeo4jRepository.updatePostStatus(postId, ContentStatus.ACTIVE.name());
        } catch (Exception e) {
            throw new Exception("Kích hoạt bài viết thành công nhưng lỗi cập nhật Neo4j: " + e.getMessage());
        }
    }
}
