package com.example.campushub.repositories.jpa;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import com.example.campushub.enums.GroupStatus;
import com.example.campushub.models.jpa.Group;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupRepository extends JpaRepository<Group, String> {
    boolean existsByNameIgnoreCase(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM Group g WHERE g.id = :groupId")
    Optional<Group> findByIdForUpdate(@Param("groupId") String groupId);

    @Query("SELECT g FROM Group g " +
            "WHERE (:status IS NULL OR g.status = :status) " +
            "AND (:query IS NULL OR :query = '' " +
            "OR LOWER(g.id) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(g.name) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Group> searchAdminGroups(@Param("query") String query,
                                  @Param("status") GroupStatus status,
                                  Pageable pageable);
}
