package com.example.campushub.repositories.neo4j;

import java.util.List;
import java.util.Set;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.campushub.dtos.record.TopicDistributionProjection;
import com.example.campushub.models.neo4j.InterestNode;
import com.example.campushub.responses.TopicResponse;

@Repository
public interface InterestNeo4jRepository extends Neo4jRepository<InterestNode, String> {
        long countByNameIn(Set<String> names);

        @Query("MATCH (t:Interest)-[:SPECIFIC_OF]->(:Category {name: 'Sở thích'}) " +
                        "RETURN t.name AS topicName, " +
                        "t.imageUrl AS imageUrl, " +
                        "count{(t)<-[:INTERESTED_IN]-()} AS followerCount")
        List<TopicResponse> findLeafTags();

        @Query("MATCH (t:Interest)-[:SPECIFIC_OF]->(:Category {name: 'Sở thích'}) RETURN t.name as tag ORDER BY t.name")
        List<String> findLeafTagsToList();

        @Query("MATCH (u:User {id: $userId})-[:INTERESTED_IN]->(t:Interest)-[:SPECIFIC_OF]->(:Category {name: 'Sở thích'}) " +
                        "RETURN t.name AS topicName, " +
                        "t.imageUrl AS imageUrl, " +
                        "count{(t)<-[:INTERESTED_IN]-()} AS followerCount")
        List<TopicResponse> getTopicUserCounts(@Param("userId") String userId);

        @Query("MATCH (t:Interest {name: $topicName})-[:SPECIFIC_OF]->(:Category {name: 'Sở thích'}) " +
                        "RETURN t.name AS topicName, " +
                        "t.imageUrl AS imageUrl, " +
                        "count{(t)<-[:INTERESTED_IN]-()} AS followerCount")
        TopicResponse getTopicDetails(@Param("topicName") String topicName);

        @Query("MATCH (t:Interest {name: $topicName})-[:SPECIFIC_OF]->(:Category {name: 'Sở thích'}) SET t.imageUrl = $imageUrl")
        void updateTopicImage(@Param("topicName") String topicName, @Param("imageUrl") String imageUrl);

        @Query("MATCH (t:Interest)-[:SPECIFIC_OF]->(:Category {name: 'Sở thích'}) " +
                        "OPTIONAL MATCH (t)<-[:HAS_TAG]-(p:Post {status: 'ACTIVE'}) " +
                        "RETURN t.name AS label, count(DISTINCT p) AS value " +
                        "ORDER BY value DESC")
        List<TopicDistributionProjection> findActiveTopicDistribution();
}
