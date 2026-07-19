package com.example.campushub.repositories.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.campushub.models.jpa.UserFollow;
import com.example.campushub.models.jpa.UserFollowId;

@Repository
public interface UserFollowRepository extends JpaRepository<UserFollow, UserFollowId> {
    
}
