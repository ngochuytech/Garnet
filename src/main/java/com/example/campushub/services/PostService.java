package com.example.campushub.services;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.campushub.dtos.users.CreatePostDTO;
import com.example.campushub.enums.ReactionType;
import com.example.campushub.exceptions.DataNotFoundException;
import com.example.campushub.models.jpa.Post;
import com.example.campushub.models.jpa.PostReaction;
import com.example.campushub.models.jpa.PostReactionId;
import com.example.campushub.models.jpa.User;
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
        List<Post> posts = postRepository.findByUser(user);
        Map<String, String> reactions = getReactionsMap(posts, user);
        return posts.stream()
                .map(post -> PostResponse.fromPost(post, reactions.get(post.getId())))
                .collect(Collectors.toList());
    }

    public Page<PostResponse> getPostsForHomeResponses(Pageable pageable, User user) throws Exception {
        Page<Post> posts = postRepository.findAll(pageable);
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
