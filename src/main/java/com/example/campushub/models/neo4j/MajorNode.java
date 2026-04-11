package com.example.campushub.models.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Node("Major")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MajorNode {
    @Id
    private String name;

    @Relationship(type = "PART_OF", direction = Relationship.Direction.OUTGOING)
    private FacultyNode faculty;
}
