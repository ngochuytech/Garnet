package com.example.campushub.responses.profiles;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InformationResponse {
    private String fullname;
    private String avatarUrl;
    private LocalDate dateOfBirth;
    private String phone;
    private boolean gender;
    private String email;
    private String bio;
    private String department;
}
