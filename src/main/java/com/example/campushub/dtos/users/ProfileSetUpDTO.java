package com.example.campushub.dtos.users;

import java.util.List;

import lombok.Data;

@Data
public class ProfileSetUpDTO {
    private String major;

    private List<String> hobbies;
}
