package com.example.campushub.repositories.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.campushub.enums.ContentStatus;
import com.example.campushub.models.jpa.Post;
import com.example.campushub.models.jpa.User;

@Repository
public interface PostRepository extends JpaRepository<Post, String> {
    List<Post> findByUser(User user);
    List<Post> findByUserAndStatus(User user, ContentStatus status);
    Page<Post> findByStatus(ContentStatus status, Pageable pageable);
    Optional<Post> findByIdAndStatus(String id, ContentStatus status);
}
