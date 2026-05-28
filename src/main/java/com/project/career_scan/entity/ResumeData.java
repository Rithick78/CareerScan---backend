package com.project.career_scan.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "resume_data")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(name = "detected_role")
    private String detectedRole;

    @Column(name = "skills", columnDefinition = "TEXT")
    private String skills;

    @Column(name = "experience")
    private String experience;

    @Column(name = "city")
    private String city;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "resume_email")
    private String resumeEmail;

    @Column(name = "phone")
    private String phone;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @PrePersist
    public void prePersist() {
        this.uploadedAt = LocalDateTime.now();
    }
}

