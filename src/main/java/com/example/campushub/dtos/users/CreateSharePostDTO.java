package com.example.campushub.dtos.users;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateSharePostDTO {
    @NotBlank(message = "Nội dung không được bỏ trống")
    private String content;
}
