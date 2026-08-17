package com.example.campushub.repositories.neo4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import com.example.campushub.models.neo4j.PostNode;

public interface PostNeo4jRepository extends Neo4jRepository<PostNode, String> {
        @Query("MATCH (u:User {id: $userId}) " +
                        "MERGE (p:Post {id: $postId}) " +
                        "ON CREATE SET p.status = 'ACTIVE', p.createdAt = $createdAt " +
                        "MERGE (u)-[posted:POSTED]->(p) " +
                        "ON CREATE SET posted.createdAt = $createdAt " +
                        "WITH p " +
                        "MATCH (t:Interest) WHERE t.name IN $tagNames " +
                        "MERGE (p)-[:HAS_TAG]->(t)")
        void createPost(@Param("userId") String userId,
                        @Param("postId") String postId,
                        @Param("tagNames") Set<String> tagNames,
                        @Param("createdAt") LocalDateTime createdAt);

        @Query("MATCH (p:Post {id: $postId}), (g:Group {id: $groupId}) " +
                        "MERGE (p)-[:POSTED_IN]->(g)")
        void linkPostToGroup(@Param("postId") String postId, @Param("groupId") String groupId);

        @Query("MATCH (u:User {id: $userId}), (p:Post {id: $postId}) " +
                        "MERGE (u)-[r:LIKED]->(p) " +
                        "ON CREATE SET r.createdAt = $createdAt " +
                        "RETURN count(r)")
        long createLikedRelation(
                        @Param("userId") String userId,
                        @Param("postId") String postId,
                        @Param("createdAt") LocalDateTime createdAt);

        @Query("MATCH (:User {id: $userId})-[r:LIKED]->(:Post {id: $postId}) " +
                        "DELETE r")
        void deleteLikedRelation(@Param("userId") String userId, @Param("postId") String postId);

        @Query("MATCH (u:User {id: $userId}), (p:Post {id: $postId}) " +
                        "MERGE (u)-[r:COMMENTED]->(p) " +
                        "ON CREATE SET r.createdAt = $createdAt " +
                        "RETURN count(r)")
        long createCommentedRelation(
                        @Param("userId") String userId,
                        @Param("postId") String postId,
                        @Param("createdAt") LocalDateTime createdAt);

        @Query("MATCH (:User {id: $userId})-[r:COMMENTED]->(:Post {id: $postId}) " +
                        "DELETE r")
        void deleteCommentedRelation(@Param("userId") String userId, @Param("postId") String postId);

        @Query("MATCH (p:Post {id: $postId}) DETACH DELETE p")
        void deletePostById(@Param("postId") String postId);

        @Query("MATCH (u:User {id: $userId}), (originalP:Post {id: $originalPostId}) " +
                        "MERGE (sharedP:Post {id: $sharedPostId}) " +
                        "ON CREATE SET sharedP.status = 'ACTIVE', sharedP.createdAt = $createdAt " +
                        "MERGE (u)-[posted:POSTED]->(sharedP) " +
                        "ON CREATE SET posted.createdAt = $createdAt " +
                        "MERGE (sharedP)-[:QUOTES]->(originalP) " +
                        "MERGE (u)-[shared:SHARED]->(originalP) " +
                        "ON CREATE SET shared.createdAt = $createdAt " +
                        "WITH sharedP, $tagNames AS tagNames " +
                        "OPTIONAL MATCH (t:Interest) WHERE t.name IN tagNames " +
                        "WITH sharedP, collect(t) AS tags " +
                        "FOREACH (tag IN tags | MERGE (sharedP)-[:HAS_TAG]->(tag)) " +
                        "RETURN count(DISTINCT sharedP)")
        long createSharedPost(
                        @Param("userId") String userId,
                        @Param("sharedPostId") String sharedPostId,
                        @Param("originalPostId") String originalPostId,
                        @Param("tagNames") Set<String> tagNames,
                        @Param("createdAt") LocalDateTime createdAt);

        @Query("MATCH (p:Post {id: $postId}) " +
                        "SET p.status = $status " +
                        "RETURN count(p)")
        long updatePostStatus(String postId, String status);

        @Query("MATCH (t:Interest {name: $tagName})<-[:HAS_TAG]-(p:Post {status: 'ACTIVE'})<-[:POSTED]-(author:User) " +
                        "WHERE coalesce(author.status, 'ACTIVE') = 'ACTIVE' " +
                        "WITH DISTINCT p " +
                        "ORDER BY p.createdAt DESC, p.id DESC " +
                        "RETURN p.id AS id, p.createdAt AS createdAt " +
                        "LIMIT $limitPlusOne")
        List<PostCursorProjection> findLatestPostsByTagName(
                        @Param("tagName") String tagName,
                        @Param("limitPlusOne") int limitPlusOne);

        @Query("MATCH (t:Interest {name: $tagName})<-[:HAS_TAG]-(p:Post {status: 'ACTIVE'})<-[:POSTED]-(author:User) " +
                        "WHERE (p.createdAt < $cursorCreatedAt " +
                        "OR (p.createdAt = $cursorCreatedAt AND p.id < $cursorPostId)) " +
                        "AND coalesce(author.status, 'ACTIVE') = 'ACTIVE' " +
                        "WITH DISTINCT p " +
                        "ORDER BY p.createdAt DESC, p.id DESC " +
                        "RETURN p.id AS id, p.createdAt AS createdAt " +
                        "LIMIT $limitPlusOne")
        List<PostCursorProjection> findLatestPostsByTagNameAfter(
                        @Param("tagName") String tagName,
                        @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
                        @Param("cursorPostId") String cursorPostId,
                        @Param("limitPlusOne") int limitPlusOne);

        @Query("MATCH (me:User {id: $userId}) " +
                        "MATCH (author:User)-[:POSTED]->(p:Post {status: 'ACTIVE'}) " +
                        "WHERE ((me)-[:INTERESTED_IN]->(:Interest)<-[:HAS_TAG]-(p) " +
                        "OR (me)-[:FOLLOWS]->(author)) " +
                        "AND coalesce(author.status, 'ACTIVE') = 'ACTIVE' " +
                        "WITH DISTINCT p " +
                        "ORDER BY p.createdAt DESC, p.id DESC " +
                        "RETURN p.id AS id, p.createdAt AS createdAt " +
                        "LIMIT $limitPlusOne")
        List<PostCursorProjection> findLatestHomeFeedPosts(
                        @Param("userId") String userId,
                        @Param("limitPlusOne") int limitPlusOne);

        @Query("MATCH (me:User {id: $userId}) " +
                        "MATCH (author:User)-[:POSTED]->(p:Post {status: 'ACTIVE'}) " +
                        "WHERE ((me)-[:INTERESTED_IN]->(:Interest)<-[:HAS_TAG]-(p) " +
                        "OR (me)-[:FOLLOWS]->(author)) " +
                        "AND coalesce(author.status, 'ACTIVE') = 'ACTIVE' " +
                        "AND (p.createdAt < $cursorCreatedAt " +
                        "OR (p.createdAt = $cursorCreatedAt AND p.id < $cursorPostId)) " +
                        "WITH DISTINCT p " +
                        "ORDER BY p.createdAt DESC, p.id DESC " +
                        "RETURN p.id AS id, p.createdAt AS createdAt " +
                        "LIMIT $limitPlusOne")
        List<PostCursorProjection> findLatestHomeFeedPostsAfter(
                        @Param("userId") String userId,
                        @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
                        @Param("cursorPostId") String cursorPostId,
                        @Param("limitPlusOne") int limitPlusOne);

        @Query("MATCH (p:Post {id: $postId})-[:HAS_TAG]->(t:Interest) RETURN t.name")
        List<String> getTagNamesByPostId(@Param("postId") String postId);
}
