package com.project.career_scan.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

// DAY 12 UPDATE:
// Added matchLabel  → "Excellent Match", "Good Match" etc.
// Added requiredSkills → skills detected in job description
// matchScore now populated (was 0 in Day 11)

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobDTO {

    private String jobId;
    private String title;
    private String company;
    private String location;
    private String employmentType;
    private String applyLink;
    private String postedAt;
    private String description;
    private String salary;

    private int matchScore;

    // label for the score
    private String matchLabel;

    // skills detected in job description
    private List<String> requiredSkills;
}

