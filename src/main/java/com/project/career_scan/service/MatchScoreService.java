package com.project.career_scan.service;

import com.project.career_scan.dto.JobDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MatchScoreService {

    private static final List<String> KNOWN_TECH_KEYWORDS = Arrays.asList(
            // Languages
            "java", "python", "javascript", "typescript", "kotlin", "scala", "go", "rust", "c++", "c#",
            // Frontend
            "react", "angular", "vue", "nextjs", "redux", "html", "css", "tailwind", "bootstrap",
            "shadcn", "jquery", "webpack", "vite",
            // Backend
            "spring", "spring boot", "spring security", "node", "nodejs", "express", "django",
            "fastapi", "flask", "hibernate", "jpa", "rest", "rest api", "graphql", "microservices",
            // Database
            "mysql", "postgresql", "mongodb", "redis", "elasticsearch", "oracle", "sql", "nosql",
            "jdbc", "hibernate",
            // Cloud & DevOps
            "aws", "azure", "gcp", "docker", "kubernetes", "jenkins", "ci/cd", "git", "github",
            "linux", "terraform", "ansible",
            // Mobile
            "android", "ios", "flutter", "react native", "swift",
            // Tools & Concepts
            "jwt", "oauth", "maven", "gradle", "junit", "mockito", "agile", "scrum",
            "kafka", "rabbitmq", "api", "restful", "json", "xml"
    );

    public int calculateMatchScore(List<String> resumeSkills, JobDTO job) {

        if (resumeSkills == null || resumeSkills.isEmpty()) {
            log.warn("Resume skills list is empty. Cannot calculate match score.");
            return 0;
        }

        List<String> resumeLower = resumeSkills.stream()
                .map(s -> s.toLowerCase().trim())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        String jobText = buildJobSearchText(job);

        List<String> matchedSkills   = new ArrayList<>();
        List<String> unmatchedSkills = new ArrayList<>();

        for (String skill : resumeLower) {
            boolean matched = jobText.contains(skill) ||
                    KNOWN_TECH_KEYWORDS.stream()
                            .anyMatch(k -> k.contains(skill) && jobText.contains(k));
            if (matched) {
                matchedSkills.add(skill);
            } else {
                unmatchedSkills.add(skill);
            }
        }

        double baseScore = ((double) matchedSkills.size() / resumeLower.size()) * 100;

        double titleBonus = calculateTitleBonus(job.getTitle(), resumeSkills);

        double descBonus = 0;
        if (matchedSkills.size() >= 3) descBonus = 10;

        int finalScore = (int) Math.min(100, Math.round(baseScore + titleBonus + descBonus));

        log.debug("Match score for '{}' at '{}': {}% " +
                        "(matched={}, unmatched={}, titleBonus={}, descBonus={})",
                job.getTitle(), job.getCompany(),
                finalScore, matchedSkills, unmatchedSkills, titleBonus, descBonus);

        return finalScore;
    }

    private String buildJobSearchText(JobDTO job) {
        StringBuilder sb = new StringBuilder();
        if (job.getTitle() != null)          sb.append(job.getTitle()).append(" ");
        if (job.getDescription() != null)    sb.append(job.getDescription()).append(" ");
        if (job.getCompany() != null)        sb.append(job.getCompany()).append(" ");
        if (job.getRequiredSkills() != null) sb.append(String.join(" ", job.getRequiredSkills())).append(" ");
        if (job.getTitle() != null)          sb.append(job.getTitle()).append(" ")
                .append(job.getTitle()).append(" ");
        return sb.toString().toLowerCase();
    }

    private double calculateTitleBonus(String jobTitle, List<String> resumeSkills) {
        if (jobTitle == null || resumeSkills == null) return 0;
        String titleLower = jobTitle.toLowerCase();
        int bonusCount = 0;
        for (String skill : resumeSkills) {
            if (titleLower.contains(skill.toLowerCase())) {
                bonusCount++;
            }
        }
        return Math.min(20.0, bonusCount * 10.0);
    }

    public List<String> extractSkillsFromJob(JobDTO job) {
        String jobText = buildJobSearchText(job);
        List<String> found = new ArrayList<>();

        for (String keyword : KNOWN_TECH_KEYWORDS) {
            if (jobText.contains(keyword)) {
                String display = keyword.substring(0, 1).toUpperCase()
                        + keyword.substring(1);
                found.add(display);
            }
        }

        return found;
    }

    public String getScoreLabel(int score) {
        if (score >= 80) return "Excellent Match";
        if (score >= 60) return "Good Match";
        if (score >= 40) return "Fair Match";
        return "Low Match";
    }
}