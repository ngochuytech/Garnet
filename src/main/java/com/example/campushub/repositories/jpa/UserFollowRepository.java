package com.example.campushub.repositories.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.campushub.models.jpa.UserFollow;
import com.example.campushub.models.jpa.UserFollowId;

public interface UserFollowRepository extends JpaRepository<UserFollow, UserFollowId> {
    
}
