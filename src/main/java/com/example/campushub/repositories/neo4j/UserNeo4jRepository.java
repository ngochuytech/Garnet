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
           "MERGE (u)-[:INTERESTED_IN]->(t) " +
           "RETURN u")
    List<UserNode> updateUserTags(@Param("userId") String userId, @Param("tags") Set<String> tags);
}