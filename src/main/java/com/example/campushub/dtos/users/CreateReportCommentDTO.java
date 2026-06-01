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
public class CreateReportCommentDTO {
    @NotBlank(message = "Reason is required")
    private String reason;

    private String description;
}
