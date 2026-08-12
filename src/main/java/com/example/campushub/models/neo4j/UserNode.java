package com.example.campushub.models.neo4j;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import com.example.campushub.enums.UserStatus;

import lombok.Data;

@Node("User")
@Data
public class UserNode {
    @Id
    private String id;

    private UserStatus status;

    private LocalDateTime createdAt;

    @Relationship(type = "MAJORS_IN", direction = Relationship.Direction.OUTGOING)
    private MajorNode major;

    @Relationship(type = "INTERESTED_IN", direction = Relationship.Direction.OUTGOING)
    private Set<InterestNode> interests = new HashSet<>();

    @Relationship(type = "FOLLOWS", direction = Relationship.Direction.OUTGOING)
    private Set<UserNode> following;

}
