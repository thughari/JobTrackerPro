package com.thughari.jobtrackerpro.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class DashboardStatsDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private long totalApplications;
    private long activePipeline;
    private long interviews;
    private long activeInterviews;
    private long offers;
}