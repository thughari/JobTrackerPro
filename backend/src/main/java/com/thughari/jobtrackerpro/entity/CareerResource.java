package com.thughari.jobtrackerpro.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "career_resources")
public class CareerResource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(nullable = false)
    private boolean fileUpload = false;

    @Column(length = 255)
    private String fileName;

    private Long fileSizeBytes;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(length = 1200)
    private String description;

    @Column(nullable = false)
    private String submittedByEmail;

    @Column(nullable = false)
    private String submittedByName;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
