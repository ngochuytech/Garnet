package com.example.campushub.repositories.jpa;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.campushub.models.jpa.UserBlock;
import com.example.campushub.models.jpa.UserBlockId;

public interface UserBlockRepository extends JpaRepository<UserBlock, UserBlockId> {
    @Query("""
            SELECT COUNT(b)
            FROM UserBlock b
            WHERE (b.id.blockerUserId = :firstUserId AND b.id.blockedUserId = :secondUserId)
               OR (b.id.blockerUserId = :secondUserId AND b.id.blockedUserId = :firstUserId)
            """)
    long countBlocksBetween(
            @Param("firstUserId") String firstUserId,
            @Param("secondUserId") String secondUserId);

    @Query("""
            SELECT CASE
                WHEN b.id.blockerUserId = :userId THEN b.id.blockedUserId
                ELSE b.id.blockerUserId
            END
            FROM UserBlock b
            WHERE b.id.blockerUserId = :userId OR b.id.blockedUserId = :userId
            """)
    Set<String> findBlockedCounterpartIds(@Param("userId") String userId);
}
