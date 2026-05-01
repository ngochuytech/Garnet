package com.example.campushub.repositories.neo4j;

import java.util.List;
import java.util.Set;

import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.campushub.dtos.record.PostTagsDTO;
import com.example.campushub.models.neo4j.PostNode;

import org.springframework.data.neo4j.repository.Neo4jRepository;

@Repository
public interface PostNeo4jRepository extends Neo4jRepository<PostNode, String> {
        @Query("MATCH (u:User {id: $userId}) " +
                        "MERGE (p:Post {id: $postId}) " +
                        "ON CREATE SET p.status = 'ACTIVE' " +
                        "MERGE (u)-[:POSTED]->(p) " +
                        "WITH p " +
                        "MATCH (t:Tag) WHERE t.name IN $tagNames " +
                        "MERGE (p)-[:HAS_TAG]->(t)")
        void createPost(@Param("userId") String userId,
                        @Param("postId") String postId,
                        @Param("tagNames") Set<String> tagNames);

        @Query("MATCH (u:User {id: $userId}), (originalP:Post {id: $originalPostId}) " +
                        "MERGE (sharedP:Post {id: $sharedPostId}) " +
                        "ON CREATE SET sharedP.status = 'ACTIVE' " +
                        "MERGE (u)-[:POSTED]->(sharedP) " +
                        "MERGE (sharedP)-[:QUOTES]->(originalP) " +
                        "WITH sharedP " +
                        "MATCH (t:Tag) WHERE t.name IN $tagNames " +
                        "MERGE (sharedP)-[:HAS_TAG]->(t)")
        void createSharedPost(
                        @Param("userId") String userId,
                        @Param("sharedPostId") String sharedPostId,
                        @Param("originalPostId") String originalPostId,
                        @Param("tagNames") Set<String> tagNames);

        @Query("MATCH (p:Post {id: $postId}) " +
                        "SET p.status = $status")
        void updatePostStatus(String postId, String status);

        @Query("MATCH (t:Tag {name: $tagName})<-[:HAS_TAG]-(p:Post {status: 'ACTIVE'})  " +
                        "RETURN p.id " +
                        "SKIP $offset " +
                        "LIMIT $limitPlusOne")
        List<String> findActivePostIdsByTagName(@Param("tagName") String tagName,  @Param("offset") long offset, @Param("limitPlusOne") int limitPlusOne);

        @Query("MATCH (p:Post {id: $postId})-[:HAS_TAG]->(t:Tag) RETURN t.name")
        List<String> getTagNamesByPostId(@Param("postId") String postId);

        @Query("MATCH (p:Post) WHERE p.id IN $postIds " +
                        "OPTIONAL MATCH (p)-[:HAS_TAG]->(t:Tag) " +
                        "RETURN p.id AS postId, collect(t.name) AS tagNames")
        List<PostTagsDTO> findTagsByPostIds(@Param("postIds") List<String> postIds);
}
