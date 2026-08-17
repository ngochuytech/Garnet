package com.example.campushub.repositories.neo4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import com.example.campushub.models.neo4j.UserNode;
import com.example.campushub.repositories.neo4j.projections.FriendSuggestionCandidate;
import com.example.campushub.responses.FollowStats;

public interface UserNeo4jRepository extends Neo4jRepository<UserNode, String> {
       @Query("MERGE (u:User {id: $userId}) " +
                     "WITH u, $majorName AS majorName, " +
                     "CASE WHEN $hobbies IS NULL THEN [] ELSE $hobbies END AS hobbies " +
                     "OPTIONAL MATCH (interest:Interest) " +
                     "WHERE interest.name IN hobbies " +
                     "WITH u, majorName, collect(interest) AS interests " +
                     "FOREACH (_ IN CASE WHEN majorName IS NOT NULL AND trim(majorName) <> '' THEN [1] ELSE [] END | " +
                     "  MERGE (u)-[:MAJORS_IN]->(:Major {name: majorName}) " +
                     ") " +
                     "FOREACH (interest IN interests | " +
                     "  MERGE (u)-[:INTERESTED_IN]->(interest) " +
                     ") " +
                     "RETURN u")
       List<UserNode> updateUserProfileGraph(
                     @Param("userId") String userId,
                     @Param("majorName") String majorName,
                     @Param("hobbies") Set<String> hobbies);

       @Query("""
                     MERGE (u:User {id: $userId})
                     SET u.status = coalesce($status, u.status)
                     WITH u
                     OPTIONAL MATCH (u)-[oldInterest:INTERESTED_IN]->(:Interest)
                     DELETE oldInterest
                     WITH u, CASE WHEN $hobbies IS NULL THEN [] ELSE $hobbies END AS hobbies, $majorName AS majorName
                     OPTIONAL MATCH (u)-[oldMajor:MAJORS_IN]->(:Major)
                     DELETE oldMajor
                     WITH u, hobbies, majorName
                     FOREACH (_ IN CASE WHEN majorName IS NOT NULL AND trim(majorName) <> '' THEN [1] ELSE [] END |
                         MERGE (m:Major {name: majorName})
                         MERGE (u)-[:MAJORS_IN]->(m)
                     )
                     WITH u, hobbies
                     UNWIND hobbies AS hobby
                     MATCH (t:Interest {name: hobby})
                     MERGE (u)-[:INTERESTED_IN]->(t)
                     RETURN count(t)
                                      """)
       long replaceUserProfileGraph(
                     @Param("userId") String userId,
                     @Param("majorName") String majorName,
                     @Param("hobbies") Set<String> hobbies,
                     @Param("status") String status);

       @Query("MERGE (u:User {id: $userId}) " +
                     "SET u.status = $status " +
                     "RETURN count(u)")
       long updateUserStatus(@Param("userId") String userId, @Param("status") String status);

       @Query("""
                     MERGE (u:User {id: $userId})
                     SET u.fullName = $fullName,
                         u.avatarUrl = $avatarUrl
                     RETURN count(u)
                     """)
       long updateUserDisplay(
                     @Param("userId") String userId,
                     @Param("fullName") String fullName,
                     @Param("avatarUrl") String avatarUrl);

       @Query("""
                     MATCH (me:User {id: $userId})
                     CALL {
                         WITH me
                         MATCH (me)-[:FOLLOWS]->(bridge:User)-[:FOLLOWS]->(candidate:User)
                         WHERE candidate <> me
                         RETURN candidate.id AS candidateId,
                                count(DISTINCT bridge) AS mutualCount,
                                0 AS sameMajorCount,
                                0 AS sharedInterestCount,
                                0 AS sharedGroupCount
                         UNION ALL
                         WITH me
                         MATCH (me)-[:MAJORS_IN]->(:Major)<-[:MAJORS_IN]-(candidate:User)
                         WHERE candidate <> me
                         RETURN candidate.id AS candidateId,
                                0 AS mutualCount,
                                1 AS sameMajorCount,
                                0 AS sharedInterestCount,
                                0 AS sharedGroupCount
                         UNION ALL
                         WITH me
                         MATCH (me)-[:INTERESTED_IN]->(interest:Interest)<-[:INTERESTED_IN]-(candidate:User)
                         WHERE candidate <> me
                         RETURN candidate.id AS candidateId,
                                0 AS mutualCount,
                                0 AS sameMajorCount,
                                count(DISTINCT interest) AS sharedInterestCount,
                                0 AS sharedGroupCount
                         UNION ALL
                         WITH me
                         MATCH (me)-[:JOINED_GROUP]->(group:Group)<-[:JOINED_GROUP]-(candidate:User)
                         WHERE candidate <> me
                         RETURN candidate.id AS candidateId,
                                0 AS mutualCount,
                                0 AS sameMajorCount,
                                0 AS sharedInterestCount,
                                count(DISTINCT group) AS sharedGroupCount
                     }
                     WITH me, candidateId,
                          max(mutualCount) AS mutualCount,
                          max(sameMajorCount) AS sameMajorCount,
                          max(sharedInterestCount) AS sharedInterestCount,
                          max(sharedGroupCount) AS sharedGroupCount
                     MATCH (candidate:User {id: candidateId})
                     WHERE coalesce(candidate.status, 'ACTIVE') = 'ACTIVE'
                       AND NOT (me)-[:FOLLOWS]->(candidate)
                     WITH candidate, mutualCount, sameMajorCount, sharedInterestCount, sharedGroupCount
                     ORDER BY mutualCount DESC,
                              sameMajorCount DESC,
                              sharedInterestCount DESC,
                              sharedGroupCount DESC,
                              candidate.id ASC
                     LIMIT $candidatePoolSize
                     WITH candidate, mutualCount, sameMajorCount, sharedInterestCount, sharedGroupCount, rand() AS randomOrder
                     ORDER BY randomOrder
                     LIMIT $limit
                     RETURN candidate.id AS id,
                            candidate.fullName AS fullName,
                            candidate.avatarUrl AS avatarUrl,
                            head([(candidate)-[:MAJORS_IN]->(major:Major) | major.name]) AS majorName
                     """)
       List<FriendSuggestionCandidate> findFriendSuggestionCandidates(
                     @Param("userId") String userId,
                     @Param("candidatePoolSize") int candidatePoolSize,
                     @Param("limit") int limit);

       @Query("""
                     MATCH (me:User {id: $userId}), (candidate:User)
                     WHERE candidate <> me
                       AND coalesce(candidate.status, 'ACTIVE') = 'ACTIVE'
                       AND NOT (me)-[:FOLLOWS]->(candidate)
                       AND NOT candidate.id IN $excludedUserIds
                     WITH candidate, rand() AS randomOrder
                     ORDER BY randomOrder
                     LIMIT $limit
                     RETURN candidate.id AS id,
                            candidate.fullName AS fullName,
                            candidate.avatarUrl AS avatarUrl,
                            head([(candidate)-[:MAJORS_IN]->(major:Major) | major.name]) AS majorName
                     """)
       List<FriendSuggestionCandidate> findRandomFriendSuggestionCandidates(
                     @Param("userId") String userId,
                     @Param("excludedUserIds") List<String> excludedUserIds,
                     @Param("limit") int limit);

        @Query("""
                      UNWIND $users AS row
                      OPTIONAL MATCH (u:User {id: row.userId})
                      FOREACH (_ IN CASE WHEN u IS NULL THEN [] ELSE [1] END |
                          SET u.createdAt = row.createdAt
                      )
                      RETURN count(DISTINCT CASE WHEN u IS NULL THEN null ELSE row.userId END)
                      """)
        long syncUserCreatedAt(@Param("users") List<Map<String, Object>> users);

        @Query("MATCH (:User {id: $userId})-[:INTERESTED_IN]->(t:Interest) "
                     +
                     "RETURN t.name")
       Set<String> findUserInterestNames(@Param("userId") String userId);

       // 3. Xóa các topic cũ đang liên kết mà không có trong danh sách mới
       @Query("MATCH (u:User {id: $userId})-[r:INTERESTED_IN]->(t:Interest) " +
                     "WHERE NOT t.name IN CASE WHEN $topics IS NULL THEN [] ELSE $topics END " +
                     "DELETE r")
       void removeOldTopics(@Param("userId") String userId, @Param("topics") Set<String> topics);

       // 4. Thêm các topic mới
       @Query("MERGE (u:User {id: $userId}) " +
                     "UNWIND CASE WHEN $topics IS NULL THEN [] ELSE $topics END AS topicName " +
                     "MATCH (newTag:Interest {name: topicName}) " +
                     "MERGE (u)-[:INTERESTED_IN]->(newTag)")
       void addNewTopics(@Param("userId") String userId, @Param("topics") Set<String> topics);

       // 5. Tạo lượt theo dõi mới người dùng
       @Query("MATCH (a:User {id: $followerId}), (b:User {id: $targetId}) " +
                     "MERGE (a)-[r:FOLLOWS]->(b) " +
                     "ON CREATE SET r.createdAt = $createdAt " +
                     "RETURN count(r) > 0")
       boolean followUser(
                     @Param("followerId") String followerId,
                     @Param("targetId") String targetId,
                     @Param("createdAt") LocalDateTime createdAt);

       // 6. Hủy theo dõi
       @Query("MATCH (a:User {id: $followerId})-[r:FOLLOWS]->(b:User {id: $targetId}) " +
                     "DELETE r")
       void unfollowUser(@Param("followerId") String followerId, @Param("targetId") String targetId);

       // 7. Kiểm tra trạng thái (Trả về true nếu A đang follow B)
       @Query("MATCH (a:User {id: $followerId})-[r:FOLLOWS]->(b:User {id: $targetId}) RETURN count(r) > 0")
       boolean isFollowing(@Param("followerId") String followerId, @Param("targetId") String targetId);

       @Query("MATCH (me:User {id: $followerId})-[:FOLLOWS]->(target:User) " +
                     "WHERE target.id IN $targetIds " +
                     "RETURN target.id")
       Set<String> findFollowingIdsInList(@Param("followerId") String followerId,
                     @Param("targetIds") List<String> targetIds);

       // 8. Đếm số người đang theo dõi và người theo dõi
       @Query("MATCH (:User {id: $userId})-[FOLLOWS]->(f:User) RETURN count(f)")
       long countFollowing(@Param("userId") String userId);

       @Query("MATCH (f:User)-[FOLLOWS]->(:User {id: $userId}) RETURN COUNT(f)")
       long countFollowers(@Param("userId") String userId);

       @Query("MATCH (u:User {id: $userId}) " +
                     "RETURN COUNT { (u)-[:FOLLOWS]->() } AS followingCount, " +
                     "COUNT { (u)<-[:FOLLOWS]-() } AS followersCount")
       FollowStats getFollowStats(@Param("userId") String userId);

       // 11. Lấy danh sách ID những người mà User đang theo dõi (Following)
       @Query("MATCH (u:User {id: $userId})-[:FOLLOWS]->(f:User) " +
                     "RETURN f.id " +
                     "ORDER BY f.id ASC " +
                     "SKIP $offset LIMIT $limitPlusOne")
       List<String> findFollowingIdsPaging(
                     @Param("userId") String userId,
                     @Param("offset") long offset,
                     @Param("limitPlusOne") int limitPlusOne);

       // Lấy danh sách ID những người đang theo dõi User (Followers)
       @Query("MATCH (f:User)-[:FOLLOWS]->(u:User {id: $userId}) " +
                     "RETURN f.id " +
                     "ORDER BY f.id ASC " +
                     "SKIP $offset LIMIT $limitPlusOne")
       List<String> findFollowerIdsPaging(
                     @Param("userId") String userId,
                     @Param("offset") long offset,
                     @Param("limitPlusOne") int limitPlusOne);

}
