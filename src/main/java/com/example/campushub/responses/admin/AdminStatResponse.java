package com.example.campushub.responses.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdminStatResponse {
    private long totalUsers;
    private long weeklyUsers;
    private long weeklyPosts;
    private long totalReports;
    private long weeklyInteractions;
    private double userGrowthPercent;
    private double postGrowthPercent;
    private double interactionGrowthPercent;
        private long weeklyReports;
    private double reportGrowthPercent;
}
