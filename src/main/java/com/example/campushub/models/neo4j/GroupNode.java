package com.example.campushub.models.neo4j;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

@Node("Group")
@Data
@Builder
public class GroupNode {
    @Id
    private String id;

    @Property("name")
    private String name;
}