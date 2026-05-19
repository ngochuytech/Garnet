package com.example.campushub.dtos.users;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateGroupNameDTO {
    @NotBlank(message = "Tên nhóm không được để trống")
    private String name;
}
