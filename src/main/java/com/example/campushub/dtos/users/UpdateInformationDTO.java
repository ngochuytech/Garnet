package com.example.campushub.dtos.users;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateInformationDTO {
    @NotBlank(message = "Full name is required")
    private String fullname;

    private LocalDate dateOfBirth;

    private String phone;

    private String gender;
}
