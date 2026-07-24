package com.example.campushub.services;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.example.campushub.dtos.record.posts.PostCreatedPayload;
import com.example.campushub.enums.GroupStatus;
import com.example.campushub.enums.MemberStatus;
import com.example.campushub.enums.Neo4jEventType;
import com.example.campushub.enums.ReactionType;
import com.example.campushub.enums.UserStatus;
import com.example.campushub.exceptions.BadRequestException;
import com.example.campushub.models.jpa.Group;
import com.example.campushub.models.jpa.GroupMember;
import com.example.campushub.models.jpa.Neo4jSyncEvent;
import com.example.campushub.models.jpa.Post;
import com.example.campushub.models.jpa.PostReaction;
import com.example.campushub.models.jpa.PostReactionId;
import com.example.campushub.models.jpa.PostTag;
import com.example.campushub.models.jpa.PostTagId;
import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.GroupMemberRepository;
import com.example.campushub.repositories.jpa.Neo4jSyncEventRepository;
import com.example.campushub.repositories.jpa.PostReactionRepository;
import com.example.campushub.repositories.jpa.PostRepository;
import com.example.campushub.repositories.jpa.PostTagRepository;
import com.example.campushub.repositories.jpa.UserInterestRepository;
import com.example.campushub.repositories.jpa.UserRepository;
import com.example.campushub.repositories.neo4j.InterestNeo4jRepository;

import lombok.RequiredArgsConstructor;
import net.datafaker.Faker;
import tools.jackson.databind.ObjectMapper;

@Service
@Profile("dev")
@RequiredArgsConstructor
public class PostSeeder {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostTagRepository postTagRepository;
    private final PostReactionRepository postReactionRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final Neo4jSyncEventRepository neo4jSyncEventRepository;
    private final InterestNeo4jRepository tagNeo4jRepository;
    private final UserInterestRepository userInterestRepository;
    private final Faker faker;
    private final ObjectMapper objectMapper;

    private String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert object to JSON", e);
        }
    }

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
        Set<String> interests = userInterestRepository.findInterestNamesByUserId(userId);
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
                "Mình đang tìm cách học %s hiệu quả hơn trong học kỳ này.",
                "Vừa rồi mình có thử áp dụng %s vào một bài tập nhỏ và thấy khá thú vị.",
                "Có ai trong CampusHub đang quan tâm đến %s không?",
                "Mình đang gom tài liệu và kinh nghiệm liên quan đến %s.",
                "Sau một buổi trao đổi với bạn bè, mình thấy %s có nhiều điểm đáng để đào sâu.");

        List<String> details = List.of(
                "Phần khó nhất hiện tại là biết bắt đầu từ đâu, nên mình muốn nghe cách mọi người chia nhỏ nội dung và luyện tập mỗi ngày.",
                "Mình thấy học theo ví dụ thực tế dễ nhớ hơn lý thuyết thuần túy, nhưng vẫn cần một lộ trình rõ ràng để không bị lan man.",
                "Nếu có checklist, repo mẫu, slide môn học hoặc bài viết hay thì mọi người để lại giúp mình với.",
                "Mình muốn thử làm một mini project trong 1-2 tuần để vừa học vừa có sản phẩm đưa vào portfolio.",
                "Chủ đề này có vẻ hợp để lập nhóm học nhỏ, mọi người có thể cùng đặt mục tiêu và review tiến độ hàng tuần.");

        List<String> questions = List.of(
                "Mọi người thường dùng nguồn nào để học %s?",
                "Nếu bắt đầu lại từ đầu với %s, bạn sẽ học theo thứ tự nào?",
                "Có lỗi sai nào khi học %s mà người mới nên tránh không?",
                "Ai có kinh nghiệm làm project về %s thì chia sẻ giúp mình vài tip nhé.",
                "Theo mọi người, nên học %s một mình hay lập nhóm học sẽ hiệu quả hơn?");

        List<String> callsToAction = List.of(
                "Bạn nào cùng mục tiêu thì comment để mình tạo một thread tổng hợp tài liệu.",
                "Nếu đủ người quan tâm, mình sẽ lập lịch học chung và chia topic theo từng buổi.",
                "Mình sẽ cập nhật lại những nguồn hữu ích nhất sau khi tổng hợp ý kiến của mọi người.",
                "Ai có góc nhìn khác thì cứ chia sẻ, mình muốn bài này thành nơi gom kinh nghiệm thật sự dùng được.",
                "Cảm ơn mọi người trước, nhất là các bạn đã từng học qua chủ đề này.");

        String opening = String.format(randomElement(openings), topicText);
        String question = String.format(randomElement(questions), primaryTopic);
        return opening + " " + randomElement(details) + " " + question + " " + randomElement(callsToAction);
    }

    private String joinTopics(List<String> topics) {
        if (topics.size() == 1) {
            return topics.get(0);
        }
        if (topics.size() == 2) {
            return topics.get(0) + " và " + topics.get(1);
        }
        return topics.get(0) + ", " + topics.get(1) + " và " + topics.get(2);
    }

    private <T> T randomElement(List<T> values) {
        return values.get(ThreadLocalRandom.current().nextInt(values.size()));
    }

    private LocalDateTime randomCreatedAtWithinLastDays(int days) {
        long maxSecondsAgo = Duration.ofDays(days).toSeconds();
        long secondsAgo = ThreadLocalRandom.current().nextLong(maxSecondsAgo + 1);
        return LocalDateTime.now().minusSeconds(secondsAgo);
    }
}
