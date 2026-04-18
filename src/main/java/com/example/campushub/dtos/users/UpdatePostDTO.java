package com.example.campushub.dtos.users;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UpdatePostDTO {
    @NotBlank(message = "Content must not be blank")
    private String content;
}
