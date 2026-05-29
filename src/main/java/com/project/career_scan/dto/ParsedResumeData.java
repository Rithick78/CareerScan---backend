package com.project.career_scan.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParsedResumeData {

    private String detectedRole;
    private List<String> skills;
    private String experience;
    private String city;
    private String summary;
    private String email;
    private String phone;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParsedResumeResponse {

        private Long id;
        private String detectedRole;
        private List<String> skills;
        private String experience;
        private String city;
        private String summary;
        private String resumeEmail;
        private String phone;
        private String fileName;
        private LocalDateTime uploadedAt;
    }
}