package com.thughari.jobtrackerpro.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class JobDataResponse {
    private List<JobDTO> jobs;
    private DashboardStatsDTO stats;
}