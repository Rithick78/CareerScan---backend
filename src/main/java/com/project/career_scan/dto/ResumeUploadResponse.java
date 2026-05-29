package com.project.career_scan.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeUploadResponse {
    private String message;
    private String fileName;
    private String filePath;
    private long fileSizeKb;
    private ParsedResumeData.ParsedResumeResponse parsedData;
}
