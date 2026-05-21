package com.example.campushub.dtos.admin;

import com.example.campushub.enums.GroupModerationAction;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AdminGroupReportDTO {
    @NotBlank(message = "Lý do vi phạm là bắt buộc")
    private String reason;

    private String description;

    @NotBlank(message = "Biện pháp xử lý là bắt buộc")
    private String adminNotes;

    private GroupModerationAction action = GroupModerationAction.ARCHIVE;
}
