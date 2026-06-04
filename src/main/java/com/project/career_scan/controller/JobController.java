package com.project.career_scan.controller;

import com.project.career_scan.dto.JobDTO;
import com.project.career_scan.dto.JobSearchResponse;
import com.project.career_scan.dto.ParsedResumeData;
import com.project.career_scan.service.JobSearchService;
import com.project.career_scan.service.ResumeDataService;
import com.project.career_scan.service.SavedJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@Slf4j
public class JobController {

    private final JobSearchService jobSearchService;
    private final ResumeDataService resumeDataService;
    private final SavedJobService savedJobService;

    // GET /api/jobs — matched jobs from user's resume
    @GetMapping
    public ResponseEntity<JobSearchResponse> getJobs(
            @AuthenticationPrincipal String email) {

        log.info("Job search requested by: {}", email);
        ParsedResumeData.ParsedResumeResponse resumeData = resumeDataService.getParsedResume(email);

        JobSearchResponse response = jobSearchService.searchJobs(
                resumeData.getDetectedRole(),
                resumeData.getSkills(),
                resumeData.getCity()
        );
        return ResponseEntity.ok(response);
    }

    // GET /api/jobs/test — hardcoded skills for quick testing
    @GetMapping("/test")
    public ResponseEntity<JobSearchResponse> testJobSearch() {
        log.info("Test job search called");
        JobSearchResponse response = jobSearchService.searchJobs(
                "Full Stack Developer",
                List.of("Java", "Spring Boot", "React", "MySQL"),
                "Bengaluru"
        );
        return ResponseEntity.ok(response);
    }

    // POST /api/jobs/save/{jobId} — save a job
    @PostMapping("/save/{jobId}")
    public ResponseEntity<Map<String, String>> saveJob(
            @RequestBody JobDTO job,
            @AuthenticationPrincipal String email) {

        log.info("Save job: {} by {}", job.getJobId(), email);
        String message = savedJobService.saveJob(job, email);
        return ResponseEntity.ok(Map.of("message", message));
    }

    // GET /api/jobs/saved — get all saved jobs
    @GetMapping("/saved")
    public ResponseEntity<List<JobDTO>> getSavedJobs(
            @AuthenticationPrincipal String email) {

        log.info("Get saved jobs for: {}", email);
        List<JobDTO> savedJobs = savedJobService.getSavedJobs(email);
        return ResponseEntity.ok(savedJobs);
    }

    // DELETE /api/jobs/saved/{jobId} — remove a saved job
    @DeleteMapping("/saved/{jobId}")
    public ResponseEntity<Map<String, String>> deleteSavedJob(
            @PathVariable String jobId,
            @AuthenticationPrincipal String email) {

        log.info("Delete saved job: {} by {}", jobId, email);
        savedJobService.deleteSavedJob(jobId, email);
        return ResponseEntity.ok(Map.of("message", "Job removed from saved list"));
    }
}