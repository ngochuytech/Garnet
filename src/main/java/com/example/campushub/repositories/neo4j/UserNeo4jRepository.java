package com.example.campushub.repositories.neo4j;

import java.util.List;
import java.util.Set;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import com.example.campushub.models.neo4j.UserNode;

import org.springframework.data.repository.query.Param;

@Repository
public interface UserNeo4jRepository extends Neo4jRepository<UserNode, String> {
    // 1. Nối User với Ngành học
    // Dùng MERGE cho User để đảm bảo: Nếu user chưa có trong Neo4j thì tự tạo luôn!
    @Query("MERGE (u:User {id: $userId}) " +
           "MERGE (m:Major {name: $majorName}) " + 
           "MERGE (u)-[:MAJORS_IN]->(m) " +
           "RETURN u")
    List<UserNode> updateUserMajor(@Param("userId") String userId, @Param("majorName") String majorName);

    // 2. Nối User với Sở thích / Kỹ năng
    // Dùng UNWIND để xử lý toàn bộ mảng List<String> trong 1 lần kết nối tới Database
    @Query("MERGE (u:User {id: $userId}) " +
           "WITH u UNWIND $tags AS tagName " +
           "MERGE (t:Tag {name: tagName}) " + // Dùng MERGE cho Tag để user có thể tự gõ tag mới
           "WITH u, t " +
           "WHERE NOT ()-[:SPECIFIC_OF]->(t) " + // Đảm bảo t là node lá (không có SpecificInterest nào chỉ vào nó)
           "MERGE (u)-[:INTERESTED_IN]->(t) " +
           "RETURN u")
    List<UserNode> updateUserTags(@Param("userId") String userId, @Param("tags") Set<String> tags);

    // 3. Xóa các topic cũ đang liên kết mà không có trong danh sách mới
    @Query("MATCH (u:User {id: $userId})-[r:INTERESTED_IN]->(t:Tag) " +
           "WHERE NOT t.name IN CASE WHEN $topics IS NULL THEN [] ELSE $topics END " +
           "DELETE r")
    void removeOldTopics(@Param("userId") String userId, @Param("topics") Set<String> topics);

    // 4. Thêm các topic mới 
    @Query("MATCH (u:User {id: $userId}) " +
           "UNWIND CASE WHEN $topics IS NULL THEN [] ELSE $topics END AS topicName " +
           "MATCH (newTag:Tag {name: topicName}) " +
           "WHERE NOT ()-[:SPECIFIC_OF]->(newTag) " + 
           "MERGE (u)-[:INTERESTED_IN]->(newTag)") 
    void addNewTopics(@Param("userId") String userId, @Param("topics") Set<String> topics);
}