package com.example.campushub.repositories.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.campushub.models.jpa.PostTag;
import com.example.campushub.models.jpa.PostTagId;

public interface PostTagRepository extends JpaRepository<PostTag, PostTagId> {
    List<PostTag> findByIdPostIdIn(List<String> postIds);

    List<PostTag> findByIdTagName(String tagName);

    @Query("SELECT pt.id.tagName FROM PostTag pt WHERE pt.id.postId = :postId")
    List<String> findTagNamesByPostId(@Param("postId") String postId);

    void deleteByIdPostId(String postId);   
}
