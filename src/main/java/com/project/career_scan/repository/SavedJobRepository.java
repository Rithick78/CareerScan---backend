package com.project.career_scan.repository;

import com.project.career_scan.entity.SavedJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SavedJobRepository extends JpaRepository<SavedJob, Long> {

    // Get all saved jobs for a user
    List<SavedJob> findByUserEmail(String userEmail);

    // Check if already saved — prevent duplicates
    boolean existsByUserEmailAndJobId(String userEmail, String jobId);

    Optional<SavedJob> findByUserEmailAndJobId(String userEmail, String jobId);
}