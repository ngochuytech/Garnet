package com.example.campushub.repositories.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.campushub.models.jpa.PostEditHistory;

public interface PostEditHistoryRepository extends JpaRepository<PostEditHistory, String> {
    
}
