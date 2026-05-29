package com.project.career_scan.service;

import com.project.career_scan.dto.ParsedResumeData;
import com.project.career_scan.entity.ResumeData;
import com.project.career_scan.entity.User;
import com.project.career_scan.exception.UserNotFoundException;
import com.project.career_scan.repository.ResumeDataRepository;
import com.project.career_scan.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeDataService {

    private final ResumeDataRepository resumeDataRepository;
    private final UserRepository userRepository;

    public ParsedResumeData.ParsedResumeResponse saveResumeData(
            ParsedResumeData parsedData,
            String userEmail,
            String filePath,
            String fileName) {

        log.info("Saving parsed resume data for user: {}", userEmail);

        // Find the user to get their ID
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UserNotFoundException(userEmail));

        // Convert List<String> skills → "Java,Spring Boot,React"
        // We store as comma string in DB (no extra library needed)
        String skillsAsString = "";
        if (parsedData.getSkills() != null && !parsedData.getSkills().isEmpty()) {
            skillsAsString = String.join(",", parsedData.getSkills());
        }

        // Build ResumeData entity
        ResumeData resumeData = new ResumeData();
        resumeData.setUserId(user.getId());
        resumeData.setUserEmail(userEmail);
        resumeData.setDetectedRole(parsedData.getDetectedRole());
        resumeData.setSkills(skillsAsString);
        resumeData.setExperience(parsedData.getExperience());
        resumeData.setCity(parsedData.getCity());
        resumeData.setSummary(parsedData.getSummary());
        resumeData.setResumeEmail(parsedData.getEmail());
        resumeData.setPhone(parsedData.getPhone());
        resumeData.setFilePath(filePath);
        resumeData.setFileName(fileName);

        // Save to MySQL
        ResumeData saved = resumeDataRepository.save(resumeData);
        log.info("Resume data saved to DB with id: {}", saved.getId());

        // Convert saved entity back to response DTO and return
        return mapToResponse(saved);
    }

    public ParsedResumeData.ParsedResumeResponse getParsedResume(String userEmail) {

        log.info("Fetching parsed resume for user: {}", userEmail);

        ResumeData resumeData = resumeDataRepository
                .findTopByUserEmailOrderByUploadedAtDesc(userEmail)
                .orElseThrow(() -> new RuntimeException(
                        "No resume found. Please upload your resume first."));

        return mapToResponse(resumeData);
    }

    private ParsedResumeData.ParsedResumeResponse mapToResponse(ResumeData resumeData) {

        List<String> skillsList = Collections.emptyList();

        if (resumeData.getSkills() != null && !resumeData.getSkills().isEmpty()) {
            skillsList = Arrays.stream(resumeData.getSkills().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }

        return new ParsedResumeData.ParsedResumeResponse(
                resumeData.getId(),
                resumeData.getDetectedRole(),
                skillsList,
                resumeData.getExperience(),
                resumeData.getCity(),
                resumeData.getSummary(),
                resumeData.getResumeEmail(),
                resumeData.getPhone(),
                resumeData.getFileName(),
                resumeData.getUploadedAt()
        );
    }
}

