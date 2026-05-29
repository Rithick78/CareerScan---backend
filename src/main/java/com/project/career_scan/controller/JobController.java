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
    private final SavedJobService savedJobService;   // ← add this

    // GET /api/jobs — matched jobs from resume
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

    // POST /api/jobs/save/{jobId} — save a job
    // Frontend sends the full job object in request body
    @PostMapping("/save/{jobId}")
    public ResponseEntity<Map<String, String>> saveJob(
            @RequestBody JobDTO job,
            @AuthenticationPrincipal String email) {

        log.info("Save job requested by: {} for jobId: {}", email, job.getJobId());
        String message = savedJobService.saveJob(job, email);
        return ResponseEntity.ok(Map.of("message", message));
    }

    // GET /api/jobs/saved — get all saved jobs for logged-in user
    @GetMapping("/saved")
    public ResponseEntity<List<JobDTO>> getSavedJobs(
            @AuthenticationPrincipal String email) {

        log.info("Get saved jobs requested by: {}", email);
        List<JobDTO> savedJobs = savedJobService.getSavedJobs(email);
        return ResponseEntity.ok(savedJobs);
    }

    // GET /api/jobs/test — hardcoded test
    @GetMapping("/test")
    public ResponseEntity<JobSearchResponse> testJobSearch(
            @AuthenticationPrincipal String email) {

        log.info("Test job search by: {}", email);

        JobSearchResponse response = jobSearchService.searchJobs(
                "Full Stack Developer",
                List.of("Java", "Spring Boot", "React", "MySQL"),
                "Chennai"
        );

        return ResponseEntity.ok(response);
    }

    // DELETE /api/jobs/saved/{jobId}
    @DeleteMapping("/saved/{jobId}")
    public ResponseEntity<Map<String, String>> deleteSavedJob(
            @PathVariable String jobId,
            @AuthenticationPrincipal String email) {

        log.info("Delete saved job requested by: {} jobId: {}", email, jobId);
        savedJobService.deleteSavedJob(jobId, email);
        return ResponseEntity.ok(Map.of("message", "Job removed from saved list"));
    }
}