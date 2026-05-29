package com.project.career_scan.controller;


import com.project.career_scan.dto.ParsedResumeData;
import com.project.career_scan.dto.ResumeUploadResponse;
import com.project.career_scan.service.ResumeDataService;
import com.project.career_scan.service.ResumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/resume")
@RequiredArgsConstructor
@Slf4j
public class ResumeController {

    private final ResumeService resumeService;
    private final ResumeDataService resumeDataService;

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ResumeUploadResponse> uploadResume(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal String email) {

        log.info("Upload request from: {}", email);
        ResumeUploadResponse response = resumeService.uploadResume(file, email);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/parsed")
    public ResponseEntity<ParsedResumeData.ParsedResumeResponse> getParsedResume(
            @AuthenticationPrincipal String email) {

        log.info("Fetching parsed resume for: {}", email);
        ParsedResumeData.ParsedResumeResponse response = resumeDataService.getParsedResume(email);
        return ResponseEntity.ok(response);
    }
}