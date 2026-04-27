package com.example.campushub.dtos.users;

import java.util.HashSet;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "Tags must not be null")
    @Builder.Default
    private Set<String> tags = new HashSet<>();
}
