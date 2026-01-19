package com.thughari.jobtrackerpro.entity;

import lombok.Data;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Data
@Entity
@Table(name = "jobs")
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")		//@Column(columnDefinition = "VARCHAR(36)") -- for mySQL
    private UUID id;

    private String userEmail; 

    private String company;
    private String role;
    private String location;
    private LocalDate date;

    private String status;
    private Integer stage;
    private String stageStatus;

    private Double salaryMin;
    private Double salaryMax;

    @Column(length = 2048)
    private String url;

    @Column(length = 4096)
    private String notes;
}