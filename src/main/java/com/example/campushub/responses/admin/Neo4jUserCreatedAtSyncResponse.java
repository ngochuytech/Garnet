package com.example.campushub.responses.admin;

public record Neo4jUserCreatedAtSyncResponse(
        int mysqlUserCount,
        int usersWithCreatedAt,
        long updatedNeo4jUserCount,
        long missingNeo4jUserCount,
        int usersWithoutCreatedAt) {
}
