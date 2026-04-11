package com.example.campushub.models.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import lombok.Data;

@Data
@Node("Tag")
public class TagNode {
    @Id
    private String name;
}
