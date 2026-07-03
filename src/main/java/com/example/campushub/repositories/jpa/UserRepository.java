package com.example.campushub.repositories.jpa;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.campushub.enums.UserRole;
import com.example.campushub.enums.UserStatus;
import com.example.campushub.models.jpa.User;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, String> {
    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    List<User> findByRole(UserRole role);

    List<User> findByStatus(UserStatus status);

    Page<User> findByFullNameContainingIgnoreCase(String name, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") String userId);

    @Query("SELECT u FROM User u " +
            "WHERE (:query IS NULL " +
            "OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR u.id LIKE CONCAT('%', :query, '%') " +
            "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "AND (:status IS NULL OR u.status = :status)")
    Page<User> findByQueryAndOptionalStatus(@Param("query") String query, @Param("status") UserStatus status, Pageable pageable);

    @Query("SELECT COUNT(u) FROM User u " +
            "WHERE u.createdAt >= :start AND u.createdAt < :end")
    long countUserBetweenStartAndEnd(@Param("start") LocalDateTime start,@Param("end") LocalDateTime end);
}
