package com.example.campushub.repositories.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.campushub.models.jpa.Post;
import com.example.campushub.models.jpa.PostReaction;
import com.example.campushub.models.jpa.PostReactionId;

import com.example.campushub.models.jpa.User;
import java.util.List;

@Repository
public interface PostReactionRepository extends JpaRepository<PostReaction, PostReactionId> {
    PostReaction findByPostAndUser(Post post, User user);
    List<PostReaction> findByPostInAndUser(List<Post> posts, User user);
    void deleteByPostAndUser(Post post, User user);
}
