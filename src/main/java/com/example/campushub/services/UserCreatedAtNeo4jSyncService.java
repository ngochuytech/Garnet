package com.example.campushub.services;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.UserRepository;
import com.example.campushub.repositories.neo4j.UserNeo4jRepository;
import com.example.campushub.responses.admin.Neo4jUserCreatedAtSyncResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserCreatedAtNeo4jSyncService {
    private static final int BATCH_SIZE = 500;

    private final UserRepository userRepository;
    private final UserNeo4jRepository userNeo4jRepository;

    /**
     * Copies MySQL users.created_at into existing Neo4j User nodes. Missing Neo4j
     * nodes are intentionally not created because this operation only owns createdAt.
     */
    public Neo4jUserCreatedAtSyncResponse syncCreatedAtToNeo4j() {
        int mysqlUserCount = 0;
        int usersWithCreatedAt = 0;
        long updatedNeo4jUserCount = 0;

        Pageable pageable = PageRequest.of(0, BATCH_SIZE, Sort.by("id").ascending());
        Page<User> page;
        do {
            page = userRepository.findAll(pageable);
            mysqlUserCount += page.getNumberOfElements();

            List<Map<String, Object>> userRows = page.getContent().stream()
                    .filter(user -> user.getCreatedAt() != null)
                    .map(user -> Map.<String, Object>of(
                            "userId", user.getId(),
                            "createdAt", user.getCreatedAt()))
                    .toList();

            usersWithCreatedAt += userRows.size();
            if (!userRows.isEmpty()) {
                updatedNeo4jUserCount += userNeo4jRepository.syncUserCreatedAt(userRows);
            }

            pageable = page.nextPageable();
        } while (page.hasNext());

        return new Neo4jUserCreatedAtSyncResponse(
                mysqlUserCount,
                usersWithCreatedAt,
                updatedNeo4jUserCount,
                usersWithCreatedAt - updatedNeo4jUserCount,
                mysqlUserCount - usersWithCreatedAt);
    }
}
