package com.project.career_scan.service;

import com.project.career_scan.dto.JobDTO;
import com.project.career_scan.entity.SavedJob;
import com.project.career_scan.repository.SavedJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SavedJobService {

    private final SavedJobRepository savedJobRepository;

    // Add this method to SavedJobService
    public void deleteSavedJob(String jobId, String userEmail) {

        SavedJob savedJob = savedJobRepository
                .findByUserEmailAndJobId(userEmail, jobId)
                .orElseThrow(() -> new RuntimeException("Saved job not found"));

        savedJobRepository.delete(savedJob);
        log.info("Saved job deleted for user: {} jobId: {}", userEmail, jobId);
    }

    // Save a job for the logged-in user
    public String saveJob(JobDTO job, String userEmail) {

        String safeJobId = job.getJobId() != null && job.getJobId().length() > 100
                ? job.getJobId().substring(0, 100)
                : job.getJobId();

        // Don't save duplicates
        if (savedJobRepository.existsByUserEmailAndJobId(userEmail, job.getJobId())) {
            return "Job already saved";
        }

        SavedJob savedJob = new SavedJob();
        savedJob.setUserEmail(userEmail);
        savedJob.setJobId(safeJobId);
        savedJob.setTitle(job.getTitle());
        savedJob.setCompany(job.getCompany());
        savedJob.setLocation(job.getLocation());
        savedJob.setEmploymentType(job.getEmploymentType());
        savedJob.setApplyLink(job.getApplyLink());
        savedJob.setMatchScore(job.getMatchScore());
        savedJob.setMatchLabel(job.getMatchLabel());
        savedJob.setSalary(job.getSalary());

        savedJobRepository.save(savedJob);
        log.info("Job saved for user: {} jobId: {}", userEmail, job.getJobId());

        return "Job saved successfully";
    }

    // Get all saved jobs for user — convert entity back to JobDTO
    public List<JobDTO> getSavedJobs(String userEmail) {

        List<SavedJob> savedJobs = savedJobRepository.findByUserEmail(userEmail);

        return savedJobs.stream().map(saved -> {
            JobDTO job = new JobDTO();
            job.setJobId(saved.getJobId());
            job.setTitle(saved.getTitle());
            job.setCompany(saved.getCompany());
            job.setLocation(saved.getLocation());
            job.setEmploymentType(saved.getEmploymentType());
            job.setApplyLink(saved.getApplyLink());
            job.setMatchScore(saved.getMatchScore());
            job.setMatchLabel(saved.getMatchLabel());
            job.setSalary(saved.getSalary());
            return job;
        }).collect(Collectors.toList());
    }
}