package com.project.career_scan.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobSearchResponse {

    private String searchQuery;
    private int totalFound;
    private String matchSummary;
    private List<JobDTO> jobs;
}

