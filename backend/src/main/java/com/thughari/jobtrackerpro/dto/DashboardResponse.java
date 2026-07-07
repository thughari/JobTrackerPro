package com.thughari.jobtrackerpro.dto;
import lombok.Data;
import java.util.List;

import java.io.Serializable;

@Data
public class DashboardResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    private DashboardStatsDTO stats;
    private List<ChartData> statusChart;
    private List<ChartData> monthlyChart;
    private List<ChartData> interviewChart;
    private boolean gmailSyncInProgress;
    private String gmailSyncStatus;
}