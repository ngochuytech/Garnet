package com.example.campushub.services;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.campushub.dtos.users.CreatePostDTO;
import com.example.campushub.dtos.users.UpdatePostDTO;
import com.example.campushub.enums.ContentStatus;
import com.example.campushub.enums.ReactionType;
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

import lombok.RequiredArgsConstructor;

import java.util.stream.Collectors;
import java.util.Map;
import com.example.campushub.responses.PostResponse;

@Service
@RequiredArgsConstructor
public class PostService {
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostReactionRepository postReactionRepository;
    private final PostEditHistoryRepository postEditHistoryRepository;

    public void createPost(User user, CreatePostDTO dto) throws Exception {
        Post post = Post.builder()
                .content(dto.getContent())
                .user(user)
                .build();
        postRepository.save(post);
    }

    @Transactional("transactionManager")
    public void likePost(User user, String postId) throws Exception {
        Post post = getPostById(postId);
        PostReaction reaction = postReactionRepository.findByPostAndUser(post, user);

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
        } else if (reaction.getType() == ReactionType.DISLIKE) {
            post.setLiked(post.getLiked() + 1);
            post.setDisliked(post.getDisliked() - 1);
            postRepository.save(post);
            reaction.setType(ReactionType.LIKE);
            postReactionRepository.save(reaction);
        } else {
            post.setLiked(post.getLiked() - 1);
            postRepository.save(post);
            postReactionRepository.delete(reaction);
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
        return posts.stream()
                .map(post -> PostResponse.fromPost(post, reactions.get(post.getId())))
                .collect(Collectors.toList());
    }

    @Transactional("transactionManager")
    public void editPost(User user, String postId, UpdatePostDTO dto) throws Exception {
        Post post = getPostById(postId);
        if (!post.getUser().getId().equals(user.getId())) {
            throw new ForbiddenAccessException("Bạn không có quyền chỉnh sửa bài viết này");
        }

        if(post.getStatus() != ContentStatus.ACTIVE) {
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
        postRepository.save(post);
    }

    public Page<PostResponse> getPostsForHomeResponses(Pageable pageable, User user) throws Exception {
        Page<Post> posts = postRepository.findByStatus(ContentStatus.ACTIVE, pageable);
        Map<String, String> reactions = getReactionsMap(posts.getContent(), user);
        return posts.map(post -> PostResponse.fromPost(post, reactions.get(post.getId())));
    }

    private Map<String, String> getReactionsMap(List<Post> posts, User user) {
        if (user == null || posts.isEmpty()) {
            return Map.of();
        }
        return postReactionRepository.findByPostInAndUser(posts, user).stream()
                .collect(Collectors.toMap(r -> r.getPost().getId(), r -> r.getType().name()));
    }
}
