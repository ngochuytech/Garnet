package com.example.campushub.repositories.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.campushub.enums.Neo4jEventStatus;
import com.example.campushub.models.jpa.Neo4jSyncEvent;

public interface Neo4jSyncEventRepository extends JpaRepository<Neo4jSyncEvent, String> {
    List<Neo4jSyncEvent> findTop50ByStatusOrderByCreatedAtAsc(Neo4jEventStatus status);

    @Modifying
    @Query("""
            UPDATE Neo4jSyncEvent e
            SET e.status = :processing
            WHERE e.id = :id AND e.status = :pending
    """)
    int markProcessingIfPending(
        @Param("id") String id,
        @Param("pending") Neo4jEventStatus pending,
        @Param("processing") Neo4jEventStatus processing
    );
}
