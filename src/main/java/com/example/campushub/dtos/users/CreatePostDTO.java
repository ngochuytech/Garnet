package com.example.campushub.dtos.users;

import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePostDTO {
    @NotBlank(message = "Nội dung bài viết không được để trống")
    private String content;

    @NotNull(message = "Tags must not be null")
    private Set<String> tags;

    private String groupId;

    private List<String> videoUrls;
    private List<String> imageUrls;
}
