package com.project.career_scan.service;

import com.project.career_scan.dto.JobDTO;
import com.project.career_scan.dto.JobSearchResponse;
import com.project.career_scan.exception.JobSearchException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobSearchService {

    private final RestTemplate restTemplate;
    private final MatchScoreService matchScoreService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${adzuna.app.id}")
    private String adzunaAppId;

    @Value("${adzuna.app.key}")
    private String adzunaAppKey;

    @Value("${adzuna.api.url}")
    private String adzunaApiUrl;

    private static final int MAX_JOBS = 10;

    // ── MAIN METHOD ──────────────────────────────────────────────────────────
    public JobSearchResponse searchJobs(String role, List<String> skills, String city) {

        String cleanCity = cleanCity(city);
        String query     = buildKeywords(role, skills);

        log.info("Adzuna search → keywords: '{}', city: '{}'", query, cleanCity);

        // Level 1 — role + skills + city
        List<JobDTO> rawJobs = callAdzunaApi(query, cleanCity);

        // Level 2 — if 0 results, try without city
        if (rawJobs.isEmpty() && !cleanCity.isBlank()) {
            log.warn("0 results with city. Retrying without city...");
            rawJobs = callAdzunaApi(query, "");
        }

        // Level 3 — if still 0, try role only
        if (rawJobs.isEmpty()) {
            log.warn("Still 0 results. Retrying with role only...");
            rawJobs = callAdzunaApi(role, "");
        }

        if (rawJobs.isEmpty()) {
            return new JobSearchResponse(query, 0, "No jobs found", new ArrayList<>());
        }

        // Apply match scores to every job
        for (JobDTO job : rawJobs) {
            int score = matchScoreService.calculateMatchScore(skills, job);
            job.setMatchScore(score);
            job.setMatchLabel(matchScoreService.getScoreLabel(score));
            job.setRequiredSkills(matchScoreService.extractSkillsFromJob(job));
        }

        // Sort by match score descending, take top 10
        List<JobDTO> sorted = rawJobs.stream()
                .sorted(Comparator.comparingInt(JobDTO::getMatchScore).reversed())
                .limit(MAX_JOBS)
                .collect(Collectors.toList());

        String summary = buildMatchSummary(sorted);

        log.info("Returning {} jobs. Summary: {}", sorted.size(), summary);

        return new JobSearchResponse(query, sorted.size(), summary, sorted);
    }

    // ── CLEAN CITY ────────────────────────────────────────────────────────────
    // "Chennai, Tamil Nadu" → "Chennai"
    // "Not specified"       → ""
    private String cleanCity(String city) {
        if (city == null || city.isBlank())              return "";
        if (city.equalsIgnoreCase("Not specified"))      return "";
        if (city.equalsIgnoreCase("India"))              return "";
        if (city.contains(","))                          return city.split(",")[0].trim();
        return city.trim();
    }

    // ── BUILD KEYWORDS ────────────────────────────────────────────────────────
    // role + top 2 skills only — more keywords = fewer results
    private String buildKeywords(String role, List<String> skills) {
        StringBuilder sb = new StringBuilder();
        if (role != null && !role.isBlank()) {
            sb.append(role.trim());
        }
        if (skills != null && !skills.isEmpty()) {
            int limit = Math.min(2, skills.size());
            for (int i = 0; i < limit; i++) {
                sb.append(" ").append(skills.get(i).trim());
            }
        }
        return sb.toString().trim();
    }

    // ── CALL ADZUNA API ───────────────────────────────────────────────────────
    private List<JobDTO> callAdzunaApi(String keywords, String city) {
        try {
            // 'in' = India country code for Adzuna
            // Change 'in' to 'gb' for UK, 'us' for USA
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromUriString(adzunaApiUrl + "/in/search/1")
                    .queryParam("app_id",           adzunaAppId)
                    .queryParam("app_key",          adzunaAppKey)
                    .queryParam("results_per_page", 20)
                    .queryParam("what",             keywords)
                    .queryParam("content-type",     "application/json");

            // Only add where param if city is not empty
            if (!city.isBlank()) {
                builder.queryParam("where", city);
            }

            String url = builder.build().toUriString();

            log.info("Calling Adzuna: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, String.class);

            String body = response.getBody();
            if (body != null) {
                log.info("Adzuna response preview: {}",
                        body.substring(0, Math.min(300, body.length())));
            }

            return parseAdzunaResponse(body);

        } catch (ResourceAccessException ex) {
            log.error("Adzuna timeout: {}", ex.getMessage());
            throw new JobSearchException("Job search timed out. Try again.");

        } catch (HttpClientErrorException ex) {
            log.error("Adzuna client error {}: {}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());

            if (ex.getStatusCode().value() == 401
                    || ex.getStatusCode().value() == 403) {
                throw new JobSearchException(
                        "Invalid Adzuna API key. Check adzuna.app.id and adzuna.app.key.");
            }
            if (ex.getStatusCode().value() == 429) {
                throw new JobSearchException(
                        "Adzuna rate limit hit. Free tier: 250 requests/day.");
            }
            throw new JobSearchException("Adzuna error: " + ex.getStatusCode());

        } catch (JobSearchException ex) {
            throw ex;

        } catch (Exception ex) {
            log.error("Unexpected Adzuna error", ex);
            throw new JobSearchException("Could not fetch jobs: " + ex.getMessage(), ex);
        }
    }

    // ── PARSE ADZUNA RESPONSE ─────────────────────────────────────────────────
    private List<JobDTO> parseAdzunaResponse(String responseBody) {
        List<JobDTO> jobs = new ArrayList<>();

        if (responseBody == null || responseBody.isBlank()) return jobs;

        try {
            JsonNode root    = objectMapper.readTree(responseBody);
            JsonNode results = root.path("results");

            if (results.isMissingNode() || !results.isArray()) {
                log.warn("No results array in Adzuna response");
                return jobs;
            }

            log.info("Adzuna results count: {}", results.size());

            for (JsonNode node : results) {
                JobDTO job = mapAdzunaNodeToJobDTO(node);
                // Only add jobs that have an apply link
                if (job.getApplyLink() != null && !job.getApplyLink().isBlank()) {
                    jobs.add(job);
                }
            }

        } catch (Exception ex) {
            log.error("Failed to parse Adzuna response: {}", ex.getMessage());
            throw new JobSearchException("Could not read job results.", ex);
        }

        return jobs;
    }

    // ── MAP ADZUNA NODE → JobDTO ──────────────────────────────────────────────
    private JobDTO mapAdzunaNodeToJobDTO(JsonNode node) {
        JobDTO job = new JobDTO();

        // Adzuna uses numeric IDs
        job.setJobId(node.path("id").asText(""));
        job.setTitle(node.path("title").asText(""));

        // Company is nested: { "display_name": "TCS" }
        job.setCompany(node.path("company").path("display_name").asText("Unknown"));

        // Location is nested: { "display_name": "Bengaluru, India" }
        job.setLocation(node.path("location").path("display_name").asText("India"));

        // redirect_url is the apply link
        job.setApplyLink(node.path("redirect_url").asText(""));

        // Adzuna does not always have employment type
        job.setEmploymentType("FULLTIME");

        // Posted date
        job.setPostedAt(node.path("created").asText(""));

        // Description — trim to 300 chars
        String desc = node.path("description").asText("");
        job.setDescription(desc.length() > 300
                ? desc.substring(0, 300) + "..." : desc);

        // Salary — Adzuna gives min and max separately
        double minSal = node.path("salary_min").asDouble(0);
        double maxSal = node.path("salary_max").asDouble(0);
        if (minSal > 0 && maxSal > 0) {
            job.setSalary("₹" + (long) minSal + " - ₹" + (long) maxSal);
        } else if (minSal > 0) {
            job.setSalary("From ₹" + (long) minSal);
        } else {
            job.setSalary("Not specified");
        }

        // Match score starts at 0 — calculated after this
        job.setMatchScore(0);

        return job;
    }

    // ── BUILD MATCH SUMMARY ───────────────────────────────────────────────────
    private String buildMatchSummary(List<JobDTO> jobs) {
        long excellent = jobs.stream().filter(j -> j.getMatchScore() >= 75).count();
        long good      = jobs.stream().filter(j -> j.getMatchScore() >= 50
                && j.getMatchScore() < 75).count();
        long fair      = jobs.stream().filter(j -> j.getMatchScore() >= 25
                && j.getMatchScore() < 50).count();
        long low       = jobs.stream().filter(j -> j.getMatchScore() < 25).count();

        StringBuilder s = new StringBuilder();
        if (excellent > 0) s.append(excellent).append(" excellent, ");
        if (good > 0)      s.append(good).append(" good, ");
        if (fair > 0)      s.append(fair).append(" fair, ");
        if (low > 0)       s.append(low).append(" low ");
        s.append("match(es) found");
        return s.toString();
    }
}