package com.example.campushub.dtos.users;

import java.util.Set;

import lombok.Data;

@Data
public class ProfileSetUpDTO {
    private String major;

    private Set<String> hobbies;
}
