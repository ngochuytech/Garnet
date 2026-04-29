package com.example.campushub.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.campushub.dtos.record.PostTagsDTO;
import com.example.campushub.dtos.users.CreatePostDTO;
import com.example.campushub.dtos.users.CreateSharePostDTO;
import com.example.campushub.dtos.users.UpdatePostDTO;
import com.example.campushub.enums.ContentStatus;
import com.example.campushub.enums.NotificationType;
import com.example.campushub.enums.ReactionType;
import com.example.campushub.events.NotificationEvent;
import com.example.campushub.exceptions.DataNotFoundException;
import com.example.campushub.exceptions.ForbiddenAccessException;
import com.example.campushub.exceptions.InvalidContentStateException;
import com.example.campushub.models.jpa.Post;
import com.example.campushub.models.jpa.PostEditHistory;
import com.example.campushub.models.jpa.PostReaction;
import com.example.campushub.models.jpa.PostReactionId;
import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.PostEditHistoryRepository;
import com.example.campushub.repositories.jpa.PostReactionRepository;
import com.example.campushub.repositories.jpa.PostRepository;
import com.example.campushub.repositories.jpa.UserRepository;
import com.example.campushub.repositories.neo4j.PostNeo4jRepository;
import com.example.campushub.repositories.neo4j.TagNeo4jRepository;
import com.example.campushub.responses.PostResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PostService {
    private final PostRepository postRepository;
    private final PostNeo4jRepository postNeo4jRepository;
    private final TagNeo4jRepository tagNeo4jRepository;
    private final UserRepository userRepository;
    private final PostReactionRepository postReactionRepository;
    private final PostEditHistoryRepository postEditHistoryRepository;
    private final FileUploadService fileUploadService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void createPost(User user, CreatePostDTO dto, List<MultipartFile> images) throws Exception {
        long existingTagsCount = tagNeo4jRepository.countByNameIn(dto.getTags());
        if (existingTagsCount != dto.getTags().size()) {
            throw new DataNotFoundException("Một hoặc nhiều chủ đề không tồn tại");
        }

        Post post = Post.builder()
                .content(dto.getContent())
                .user(user)
                .build();

        if (images != null && !images.isEmpty()) {
            List<String> imageUrls = fileUploadService.uploadFiles(images, "posts");
            post.setImages(imageUrls);
        }

        postRepository.save(post);
        try {
            postNeo4jRepository.createPost(user.getId(), post.getId(), dto.getTags());
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi tạo bài viết trên Neo4j: " + e.getMessage());
        }
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
            postNeo4jRepository.createSharedPost(user.getId(), sharedPost.getId(), originalPost.getId(), dto.getTags());
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
                .map(post -> {
                    List<String> tags = tagsMap.getOrDefault(post.getId(), Collections.emptyList());
                    List<String> sharedTags = post.getSharedPost() != null
                            ? tagsMap.getOrDefault(post.getSharedPost().getId(), Collections.emptyList())
                            : null;
                    return PostResponse.fromPost(post, reactions.get(post.getId()), tags, sharedTags);
                })
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

    @Transactional("transactionManager")
    public void deletePost(User user, String postId) throws Exception {
        Post post = getPostById(postId);
        if (!post.getUser().getId().equals(user.getId())) {
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
    }

    public Page<PostResponse> getActivePostsByUserId(String userId, Pageable pageable, User currentUser)
            throws Exception {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DataNotFoundException("Người dùng không tồn tại"));
        Page<Post> posts = postRepository.findByUserAndStatus(user, ContentStatus.ACTIVE, pageable);
        Map<String, String> reactions = getReactionsMap(posts.getContent(), currentUser);
        Map<String, List<String>> tagsMap = getTagsMap(posts.getContent());
        return posts.map(post -> {
            List<String> tags = tagsMap.getOrDefault(post.getId(), Collections.emptyList());
            List<String> sharedTags = post.getSharedPost() != null
                    ? tagsMap.getOrDefault(post.getSharedPost().getId(), Collections.emptyList())
                    : null;
            return PostResponse.fromPost(post, reactions.get(post.getId()), tags, sharedTags);
        });
    }

    public Page<PostResponse> getPostsForHomeResponses(Pageable pageable, User user) throws Exception {
        Page<Post> posts = postRepository.findByStatus(ContentStatus.ACTIVE, pageable);
        Map<String, String> reactions = getReactionsMap(posts.getContent(), user);
        Map<String, List<String>> tagsMap = getTagsMap(posts.getContent());
        return posts.map(post -> {
            List<String> tags = tagsMap.getOrDefault(post.getId(), Collections.emptyList());
            List<String> sharedTags = post.getSharedPost() != null
                    ? tagsMap.getOrDefault(post.getSharedPost().getId(), Collections.emptyList())
                    : null;
            return PostResponse.fromPost(post, reactions.get(post.getId()), tags, sharedTags);
        });
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

    public Slice<PostResponse> getPostsByTopicName(User user, String topicName, Pageable pageable) throws Exception {
        List<String> postIds = postNeo4jRepository.findActivePostIdsByTagName(topicName, pageable.getOffset(), pageable.getPageSize() + 1);
        boolean hasNext = postIds.size() > pageable.getPageSize();

        if (hasNext) {
            postIds.remove(postIds.size() - 1);
        }
        if (postIds.isEmpty()) {
            return new SliceImpl<>(Collections.emptyList(), pageable, false);
        }
        List<Post> posts = postRepository.findByIdInAndStatus(postIds, ContentStatus.ACTIVE);
        posts.sort(Comparator.comparingInt(post -> postIds.indexOf(post.getId().toString())));

        Map<String, String> reactions = getReactionsMap(posts, user);
        Map<String, List<String>> tagsMap = getTagsMap(posts);

        List<PostResponse> responses = posts.stream()
                .map(post -> {
                    List<String> tags = tagsMap.getOrDefault(post.getId(), Collections.emptyList());
                    List<String> sharedTags = post.getSharedPost() != null
                            ? tagsMap.getOrDefault(post.getSharedPost().getId(), Collections.emptyList())
                            : null;
                    return PostResponse.fromPost(post, reactions.get(post.getId()), tags, sharedTags);
                })
                .collect(Collectors.toList());
        return new SliceImpl<>(responses, pageable, hasNext);
    }
}
