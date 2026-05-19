package com.example.campushub.repositories.neo4j;

import java.util.List;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import com.example.campushub.models.neo4j.MajorNode;

@Repository
public interface MajorNeo4jRepository extends Neo4jRepository<MajorNode, String> {
    
    @Query("MATCH (m:Major) RETURN m.name AS major ORDER BY m.name")
    List<String> findAllMajorNames();
}
