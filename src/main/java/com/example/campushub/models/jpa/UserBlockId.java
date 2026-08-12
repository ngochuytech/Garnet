package com.example.campushub.models.jpa;

import java.io.Serializable;

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
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
public class UserBlockId implements Serializable {
    @Column(name = "blocker_user_id")
    private String blockerUserId;

    @Column(name = "blocked_user_id")
    private String blockedUserId;
}
