package com.example.campushub.responses;

import com.example.campushub.enums.GroupStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GroupStatusResponse {
    private GroupStatus status;
    private long reportCount;
    private String adminNotes;
    private List<ReportResponse> reports;
}
