package com.example.campushub.models.neo4j;

import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import com.example.campushub.enums.ContentStatus;

import lombok.Data;

@Data
@Node("Post")
public class PostNode {
    @Id
    private String id;

    private ContentStatus status = ContentStatus.ACTIVE;

    private LocalDateTime createdAt;

    @Relationship(type = "HAS_TAG", direction = Relationship.Direction.OUTGOING)
    private Set<InterestNode> tags;
}
