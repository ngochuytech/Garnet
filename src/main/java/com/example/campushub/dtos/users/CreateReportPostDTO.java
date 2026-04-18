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
public class CreateReportPostDTO {
    @NotBlank(message = "Reason is required")
    private String reason;

    private String description;

    @NotBlank(message = "Target ID is required")
    private String targetId;

    @NotBlank(message = "Target type is required")
    private String targetType;
    
}
