package com.example.campushub.repositories.neo4j;

import java.util.List;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import com.example.campushub.models.neo4j.TagNode;

@Repository
public interface TagNeo4jRepository extends Neo4jRepository<TagNode, String> {
    
    @Query("MATCH (t:Tag) WHERE NOT ()-[:SPECIFIC_OF]->(t) RETURN t")
    List<TagNode> findLeafTags();
}
