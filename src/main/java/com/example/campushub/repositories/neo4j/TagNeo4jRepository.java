package com.example.campushub.repositories.neo4j;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import com.example.campushub.models.neo4j.TagNode;

@Repository
public interface TagNeo4jRepository extends Neo4jRepository<TagNode, String> {
    
}
