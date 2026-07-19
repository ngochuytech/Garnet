package com.example.campushub.models.jpa;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
public class UserFollowId implements Serializable{
    private String followerId;
    
    private String targetId;
}
