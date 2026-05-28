package com.project.career_scan.repository;

import com.project.career_scan.entity.ResumeData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ResumeDataRepository extends JpaRepository<ResumeData, Long> {

    // Get the most recent resume data for a user by email
    Optional<ResumeData> findTopByUserEmailOrderByUploadedAtDesc(String userEmail);

    boolean existsByUserEmail(String userEmail);

    Optional<ResumeData> findTopByUserIdOrderByUploadedAtDesc(Long userId);
}

