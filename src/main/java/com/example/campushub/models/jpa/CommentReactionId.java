package com.example.campushub.models.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CommentReactionId {
    @Column(name = "comment_id", nullable = false)
    private String commentId;

    @Column(name = "user_id", nullable = false)
    private String userId;
}
