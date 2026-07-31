package com.example.campushub.repositories.jpa;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.campushub.models.jpa.UserInterest;
import com.example.campushub.models.jpa.UserInterestId;

public interface UserInterestRepository extends JpaRepository<UserInterest, UserInterestId> {
    List<UserInterest> findByIdUserId(String userId);

    void deleteByIdUserId(String userId);

    @Query("SELECT ui.id.interestName FROM UserInterest ui WHERE ui.id.userId = :userId")
    Set<String> findInterestNamesByUserId(@Param("userId") String userId);
}
