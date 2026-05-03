package com.example.campushub.dtos.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AdminReportDTO {
    private String reason;

    @NotBlank(message = "Lý do gỡ bài viết là bắt buộc")
    private String adminNotes;
}
