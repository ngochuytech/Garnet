package com.example.campushub.dtos.users;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateGroupDTO {
    @NotBlank(message = "Tên nhóm không được để trống")
    private String name;

    private String description;
}