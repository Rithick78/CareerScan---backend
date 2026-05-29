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
import org.springframework.web.client.HttpServerErrorException;
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

    @Value("${jsearch.api.key}")
    private String jsearchApiKey;

    @Value("${jsearch.api.url}")
    private String jsearchApiUrl;

    @Value("${jsearch.api.host}")
    private String jsearchApiHost;

    private static final int MAX_JOBS = 10;

    public JobSearchResponse searchJobs(String role, List<String> skills, String city) {

        String cleanCity = cleanCity(city);

        String query = buildSearchQuery(role, skills, cleanCity);

        log.info("========================================");
        log.info("JSEARCH CALL");
        log.info("Original city : {}", city);
        log.info("Cleaned city  : {}", cleanCity);
        log.info("Final query   : {}", query);
        log.info("========================================");

        List<JobDTO> rawJobs = callJSearchApi(query);

        // If city-specific query returns 0, try without city
        if (rawJobs.isEmpty() && !cleanCity.isBlank()) {
            log.warn("0 jobs with city '{}'. Retrying without city...", cleanCity);
            String queryWithoutCity = buildSearchQuery(role, skills, "");
            log.info("Retry query: {}", queryWithoutCity);
            rawJobs = callJSearchApi(queryWithoutCity);
        }

        // If still 0, try role only — broadest possible search
        if (rawJobs.isEmpty() && role != null && !role.isBlank()) {
            log.warn("Still 0 jobs. Retrying with role only: {}", role);
            rawJobs = callJSearchApi(role + " developer India");
        }

        log.info("Total raw jobs fetched: {}", rawJobs.size());

        if (rawJobs.isEmpty()) {
            return new JobSearchResponse(query, 0, "No jobs found", new ArrayList<>());
        }

        // Apply match scores
        for (JobDTO job : rawJobs) {
            int score = matchScoreService.calculateMatchScore(skills, job);
            job.setMatchScore(score);
            job.setMatchLabel(matchScoreService.getScoreLabel(score));
            job.setRequiredSkills(matchScoreService.extractSkillsFromJob(job));
        }

        // Sort descending, top 10
        List<JobDTO> sorted = rawJobs.stream()
                .sorted(Comparator.comparingInt(JobDTO::getMatchScore).reversed())
                .limit(MAX_JOBS)
                .collect(Collectors.toList());

        String summary = buildMatchSummary(sorted);

        log.info("Returning {} jobs. Summary: {}", sorted.size(), summary);
        printRankedJobs(sorted);

        return new JobSearchResponse(query, sorted.size(), summary, sorted);
    }

    private String cleanCity(String city) {
        if (city == null || city.isBlank()) return "";
        if (city.equalsIgnoreCase("Not specified")) return "";
        if (city.equalsIgnoreCase("India")) return "";

        // If "Chennai, Tamil Nadu" → take only "Chennai"
        if (city.contains(",")) {
            return city.split(",")[0].trim();
        }

        return city.trim();
    }
    private String buildSearchQuery(String role, List<String> skills, String city) {
        StringBuilder q = new StringBuilder();

        // Add role
        if (role != null && !role.isBlank()) {
            q.append(role.trim());
        }

        if (skills != null && !skills.isEmpty()) {
            int limit = Math.min(2, skills.size());
            for (int i = 0; i < limit; i++) {
                q.append(" ").append(skills.get(i).trim());
            }
        }

        if (!city.isBlank()) {
            q.append(" ").append(city);
        }

        return q.toString().trim();
    }

    private List<JobDTO> callJSearchApi(String query) {

        try {
            String url = UriComponentsBuilder
                    .fromUriString(jsearchApiUrl)
                    .queryParam("query", query)
                    .queryParam("page", "1")
                    .queryParam("num_pages", "1")
                    .queryParam("date_posted", "all")
                    .build()
                    .toUriString();

            log.info("Calling JSearch: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-RapidAPI-Key", jsearchApiKey);
            headers.set("X-RapidAPI-Host", jsearchApiHost);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, String.class);

            String body = response.getBody();

            if (body != null) {
                log.info("JSearch preview: {}",
                        body.substring(0, Math.min(300, body.length())));
            }

            return parseJobsFromResponse(body);

        } catch (ResourceAccessException ex) {
            log.error("JSearch timeout: {}", ex.getMessage());
            throw new JobSearchException("Job search timed out. Try again.");

        } catch (HttpClientErrorException ex) {
            log.error("JSearch error {}: {}", ex.getStatusCode(),
                    ex.getResponseBodyAsString());
            if (ex.getStatusCode().value() == 401
                    || ex.getStatusCode().value() == 403) {
                throw new JobSearchException(
                        "Invalid RapidAPI key. Check jsearch.api.key in properties.");
            }
            if (ex.getStatusCode().value() == 429) {
                throw new JobSearchException(
                        "Rate limit hit. Free plan: 500 requests/month.");
            }
            throw new JobSearchException("JSearch error: " + ex.getStatusCode());

        } catch (HttpServerErrorException ex) {
            log.error("JSearch server error: {}", ex.getStatusCode());
            throw new JobSearchException("JSearch unavailable. Try again.");

        } catch (JobSearchException ex) {
            throw ex;

        } catch (Exception ex) {
            log.error("Unexpected JSearch error", ex);
            throw new JobSearchException("Could not fetch jobs: " + ex.getMessage(), ex);
        }
    }

    private List<JobDTO> parseJobsFromResponse(String responseBody) {

        List<JobDTO> jobs = new ArrayList<>();
        if (responseBody == null || responseBody.isBlank()) return jobs;

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode dataArray = root.path("data");

            if (dataArray.isMissingNode() || dataArray.isNull()
                    || !dataArray.isArray()) {
                log.warn("No data array in JSearch response");
                return jobs;
            }

            log.info("JSearch data array size: {}", dataArray.size());

            for (JsonNode jobNode : dataArray) {
                JobDTO job = mapNodeToJobDTO(jobNode);
                if (job.getApplyLink() != null && !job.getApplyLink().isBlank()) {
                    jobs.add(job);
                }
            }

        } catch (Exception ex) {
            log.error("Failed to parse JSearch response: {}", ex.getMessage());
            throw new JobSearchException("Could not read job results.", ex);
        }

        return jobs;
    }

    private JobDTO mapNodeToJobDTO(JsonNode node) {

        JobDTO job = new JobDTO();
        job.setJobId(safeText(node, "job_id"));
        job.setTitle(safeText(node, "job_title"));
        job.setCompany(safeText(node, "employer_name"));
        job.setEmploymentType(safeText(node, "job_employment_type"));
        job.setApplyLink(safeText(node, "job_apply_link"));
        job.setPostedAt(safeText(node, "job_posted_at_datetime_utc"));

        String jobCity    = safeText(node, "job_city");
        String jobState   = safeText(node, "job_state");
        String jobCountry = safeText(node, "job_country");

        if (!jobCity.isBlank() && !jobState.isBlank()) {
            job.setLocation(jobCity + ", " + jobState);
        } else if (!jobCity.isBlank()) {
            job.setLocation(jobCity + ", " + jobCountry);
        } else {
            job.setLocation(jobCountry.isBlank() ? "India" : jobCountry);
        }

        String fullDesc = safeText(node, "job_description");
        job.setDescription(fullDesc.length() > 300
                ? fullDesc.substring(0, 300) + "..." : fullDesc);

        JsonNode minSal = node.path("job_min_salary");
        JsonNode maxSal = node.path("job_max_salary");
        if (!minSal.isNull() && !minSal.isMissingNode()
                && !maxSal.isNull() && !maxSal.isMissingNode()) {
            job.setSalary("₹" + minSal.asLong() + " - ₹" + maxSal.asLong());
        } else {
            job.setSalary("Not specified");
        }

        job.setMatchScore(0);
        return job;
    }

    private String safeText(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isNull() || v.isMissingNode()) return "";
        return v.asText("").trim();
    }

    private String buildMatchSummary(List<JobDTO> jobs) {
        long excellent = jobs.stream().filter(j -> j.getMatchScore() >= 80).count();
        long good      = jobs.stream().filter(j -> j.getMatchScore() >= 60
                && j.getMatchScore() < 80).count();
        long fair      = jobs.stream().filter(j -> j.getMatchScore() >= 40
                && j.getMatchScore() < 60).count();
        long low       = jobs.stream().filter(j -> j.getMatchScore() < 40).count();

        StringBuilder s = new StringBuilder();
        if (excellent > 0) s.append(excellent).append(" excellent, ");
        if (good > 0)      s.append(good).append(" good, ");
        if (fair > 0)      s.append(fair).append(" fair, ");
        if (low > 0)       s.append(low).append(" low ");
        s.append("match(es) found");
        return s.toString();
    }

    private void printRankedJobs(List<JobDTO> jobs) {
        log.info("=== RANKED JOBS ===");
        for (int i = 0; i < jobs.size(); i++) {
            JobDTO j = jobs.get(i);
            log.info("#{} [{}%] {} | {} | {}",
                    i + 1, j.getMatchScore(), j.getTitle(),
                    j.getCompany(), j.getLocation());
        }
        log.info("=== END ===");
    }
}


