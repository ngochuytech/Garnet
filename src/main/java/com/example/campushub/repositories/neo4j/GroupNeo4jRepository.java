package com.example.campushub.repositories.neo4j;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import com.example.campushub.models.neo4j.GroupNode;

public interface GroupNeo4jRepository extends Neo4jRepository<GroupNode, String> {
    @Query("MERGE (u:User {id: $userId}) " +
            "MERGE (g:Group {id: $groupId}) " +
            "MERGE (u)-[:JOINED_GROUP]->(g)")
    void addUserToGroup(@Param("userId") String userId, @Param("groupId") String groupId);

    @Query("""
            MATCH (u:User {id: $userId})
            MATCH (g:Group {id: $groupId})
            MERGE (u)-[:JOINED_GROUP]->(g)
            RETURN count(g)
            """)
    long addExistingUserToGroup(@Param("userId") String userId, @Param("groupId") String groupId);

    @Query("MATCH (u:User {id: $userId})-[r:JOINED_GROUP]->(g:Group {id: $groupId}) DELETE r")
    void removeUserFromGroup(@Param("userId") String userId, @Param("groupId") String groupId);

    @Query("""
            MATCH (g:Group {id: $groupId})
            SET g.name = $name
            RETURN count(g)
            """)
    long updateGroupName(@Param("groupId") String groupId, @Param("name") String name);

    @Query("MATCH (g:Group {id: $groupId}) DETACH DELETE g")
    void deleteGroupById(@Param("groupId") String groupId);
}
