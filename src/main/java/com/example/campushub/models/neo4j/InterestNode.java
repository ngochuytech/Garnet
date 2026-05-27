package com.example.campushub.models.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import lombok.Data;

@Data
@Node("Interest")
public class InterestNode {
    @Id
    private String name;

    private String imageUrl;
}
