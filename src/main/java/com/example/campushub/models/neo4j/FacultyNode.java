package com.example.campushub.models.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Node("Faculty")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FacultyNode {
    @Id
    private String name;
}
