package com.project.career_scan.service;

import com.project.career_scan.dto.JobDTO;
import com.project.career_scan.dto.JobSearchResponse;
import com.project.career_scan.exception.JobSearchException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
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

    @Value("${adzuna.app.id}")
    private String adzunaAppId;

    @Value("${adzuna.app.key}")
    private String adzunaAppKey;

    @Value("${adzuna.api.url}")
    private String adzunaApiUrl;

    @Value("${arbeitnow.api.url}")
    private String arbeitnowApiUrl;

    private static final int MAX_JOBS = 10;

    public JobSearchResponse searchJobs(String role, List<String> skills, String city) {

        String cleanCity = cleanCity(city);
        String keywords  = buildKeywords(role, skills);

        log.info("==============================================");
        log.info("JOB SEARCH STARTED");
        log.info("Keywords : {}", keywords);
        log.info("City     : {}", cleanCity);
        log.info("==============================================");

        List<JobDTO> rawJobs = new ArrayList<>();

        // Source 1: JSearch
        log.info("Trying Source 1: JSearch...");
        try {
            rawJobs = callJSearchApi(keywords, cleanCity);
            if (!rawJobs.isEmpty()) {
                log.info("JSearch returned {} jobs.", rawJobs.size());
            } else {
                log.warn("JSearch returned 0 jobs. Trying next source...");
            }
        } catch (Exception ex) {
            log.warn("JSearch failed: {}. Trying next source...", ex.getMessage());
        }

        // Source 2: Adzuna
        if (rawJobs.isEmpty()) {
            log.info("Trying Source 2: Adzuna...");
            try {
                rawJobs = callAdzunaApi(keywords, cleanCity);
                if (!rawJobs.isEmpty()) {
                    log.info("Adzuna returned {} jobs.", rawJobs.size());
                } else {
                    log.warn("Adzuna returned 0 jobs. Trying next source...");
                }
            } catch (Exception ex) {
                log.warn("Adzuna failed: {}. Trying next source...", ex.getMessage());
            }
        }

        // Source 3: Arbeitnow
        if (rawJobs.isEmpty()) {
            log.info("Trying Source 3: Arbeitnow (last resort)...");
            try {
                rawJobs = callArbeitnowApi(keywords);
                if (!rawJobs.isEmpty()) {
                    log.info("Arbeitnow returned {} jobs.", rawJobs.size());
                } else {
                    log.warn("Arbeitnow also returned 0 jobs.");
                }
            } catch (Exception ex) {
                log.warn("Arbeitnow failed: {}", ex.getMessage());
            }
        }

        if (rawJobs.isEmpty()) {
            log.error("All sources failed or returned 0 jobs.");
            return new JobSearchResponse(keywords, 0, "No jobs found", new ArrayList<>());
        }

        // Score every job
        for (JobDTO job : rawJobs) {
            int score = matchScoreService.calculateMatchScore(skills, job);
            job.setMatchScore(score);
            job.setMatchLabel(matchScoreService.getScoreLabel(score));
            job.setRequiredSkills(matchScoreService.extractSkillsFromJob(job));
        }

        // Sort by score descending, return top 10
        List<JobDTO> sorted = rawJobs.stream()
                .sorted(Comparator.comparingInt(JobDTO::getMatchScore).reversed())
                .limit(MAX_JOBS)
                .collect(Collectors.toList());

        String summary = buildMatchSummary(sorted);

        log.info("Returning {} jobs. Summary: {}", sorted.size(), summary);
        log.info("==============================================");

        return new JobSearchResponse(keywords, sorted.size(), summary, sorted);
    }

    // JSEARCH
    private List<JobDTO> callJSearchApi(String keywords, String city) {

        String location = city.isBlank() ? "India" : city + ", India";
        String query = keywords + " " + location;

        String url = UriComponentsBuilder
                .fromUriString(jsearchApiUrl)
                .queryParam("query",       query)
                .queryParam("page",        "1")
                .queryParam("num_pages",   "1")
                .queryParam("date_posted", "month")
                .queryParam("country",     "IN")
                .queryParam("language",    "en")
                .build()
                .toUriString();

        log.info("JSearch URL: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RapidAPI-Key",  jsearchApiKey);
        headers.set("X-RapidAPI-Host", jsearchApiHost);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        return parseJSearchResponse(response.getBody());
    }

    private List<JobDTO> parseJSearchResponse(String body) {
        List<JobDTO> jobs = new ArrayList<>();
        if (body == null || body.isBlank()) return jobs;

        try {
            JsonNode root      = objectMapper.readTree(body);
            JsonNode dataArray = root.path("data");
            if (!dataArray.isArray()) return jobs;

            log.info("JSearch raw results: {}", dataArray.size());

            for (JsonNode node : dataArray) {
                JobDTO job = new JobDTO();
                job.setJobId("js-" + node.path("job_id").asText(""));
                job.setTitle(node.path("job_title").asText(""));
                job.setCompany(node.path("employer_name").asText(""));
                job.setEmploymentType(node.path("job_employment_type").asText("FULLTIME"));
                job.setApplyLink(node.path("job_apply_link").asText(""));
                job.setPostedAt(node.path("job_posted_at_datetime_utc").asText(""));

                String jobCity    = node.path("job_city").asText("");
                String jobCountry = node.path("job_country").asText("India");
                job.setLocation(jobCity.isBlank() ? jobCountry : jobCity + ", " + jobCountry);

                if (!jobCountry.isBlank() &&
                        !jobCountry.equalsIgnoreCase("India") &&
                        !jobCountry.equalsIgnoreCase("IN")) {
                    continue;
                }
                String desc = node.path("job_description").asText("");
                job.setDescription(desc.length() > 1000 ? desc.substring(0, 1000) + "..." : desc);

                job.setSalary("Not specified");
                job.setMatchScore(0);

                if (!job.getTitle().isBlank() && !job.getApplyLink().isBlank()) {
                    jobs.add(job);
                }
            }

        } catch (Exception ex) {
            log.error("JSearch parse error: {}", ex.getMessage());
            throw new JobSearchException("JSearch parse failed: " + ex.getMessage());
        }

        return jobs;
    }

    // ADZUNA
    private List<JobDTO> callAdzunaApi(String keywords, String city) {

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(adzunaApiUrl + "/in/search/1")
                .queryParam("app_id",           adzunaAppId)
                .queryParam("app_key",          adzunaAppKey)
                .queryParam("results_per_page", 10)
                .queryParam("what",             keywords)
                .queryParam("content-type",     "application/json");

        if (!city.isBlank()) {
            builder.queryParam("where", city);
        }

        String url = builder.build().toUriString();
        log.info("Adzuna URL: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        return parseAdzunaResponse(response.getBody());
    }

    private List<JobDTO> parseAdzunaResponse(String body) {
        List<JobDTO> jobs = new ArrayList<>();
        if (body == null || body.isBlank()) return jobs;

        try {
            JsonNode root    = objectMapper.readTree(body);
            JsonNode results = root.path("results");
            if (!results.isArray()) return jobs;

            log.info("Adzuna raw results: {}", results.size());

            for (JsonNode node : results) {
                JobDTO job = new JobDTO();
                job.setJobId("adzuna-" + node.path("id").asText(""));
                job.setTitle(node.path("title").asText(""));
                job.setCompany(node.path("company").path("display_name").asText("Unknown"));
                job.setLocation(node.path("location").path("display_name").asText("India"));
                job.setApplyLink(node.path("redirect_url").asText(""));
                job.setEmploymentType("FULLTIME");
                job.setPostedAt(node.path("created").asText(""));

                String desc = node.path("description").asText("");
                job.setDescription(desc.length() > 1000 ? desc.substring(0, 1000) + "..." : desc);

                double min = node.path("salary_min").asDouble(0);
                double max = node.path("salary_max").asDouble(0);
                job.setSalary(min > 0 ? "₹" + (long) min + " - ₹" + (long) max : "Not specified");

                job.setMatchScore(0);

                if (!job.getTitle().isBlank() && !job.getApplyLink().isBlank()) {
                    jobs.add(job);
                }
            }

        } catch (Exception ex) {
            log.error("Adzuna parse error: {}", ex.getMessage());
            throw new JobSearchException("Adzuna parse failed: " + ex.getMessage());
        }

        return jobs;
    }

    // ARBEITNOW
    private List<JobDTO> callArbeitnowApi(String keywords) {

        String url = UriComponentsBuilder
                .fromUriString(arbeitnowApiUrl)
                .queryParam("search", keywords)
                .build()
                .toUriString();

        log.info("Arbeitnow URL: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        return parseArbeitnowResponse(response.getBody());
    }

    private List<JobDTO> parseArbeitnowResponse(String body) {
        List<JobDTO> jobs = new ArrayList<>();
        if (body == null || body.isBlank()) return jobs;

        try {
            JsonNode root      = objectMapper.readTree(body);
            JsonNode dataArray = root.path("data");
            if (!dataArray.isArray()) return jobs;

            log.info("Arbeitnow raw results: {}", dataArray.size());

            for (JsonNode node : dataArray) {
                JobDTO job = new JobDTO();
                job.setJobId("arb-" + node.path("slug").asText(""));
                job.setTitle(node.path("title").asText(""));
                job.setCompany(node.path("company_name").asText(""));
                job.setLocation(node.path("location").asText("Remote"));
                job.setEmploymentType(node.path("job_types").path(0).asText("FULLTIME"));
                job.setApplyLink(node.path("url").asText(""));
                job.setPostedAt(node.path("created_at").asText(""));

                String desc = node.path("description").asText("");
                job.setDescription(desc.length() > 1000 ? desc.substring(0, 1000) + "..." : desc);

                job.setSalary("Not specified");
                job.setMatchScore(0);

                if (!job.getTitle().isBlank() && !job.getApplyLink().isBlank()) {
                    jobs.add(job);
                }
            }

        } catch (Exception ex) {
            log.error("Arbeitnow parse error: {}", ex.getMessage());
            throw new JobSearchException("Arbeitnow parse failed: " + ex.getMessage());
        }

        return jobs;
    }

    // HELPERS
    private String cleanCity(String city) {
        if (city == null || city.isBlank())         return "";
        if (city.equalsIgnoreCase("Not specified")) return "";
        if (city.equalsIgnoreCase("India"))         return "";
        if (city.equalsIgnoreCase("Remote"))        return "";
        if (city.contains(","))                     return city.split(",")[0].trim();
        return city.trim();
    }

    private String buildKeywords(String role, List<String> skills) {
        StringBuilder sb = new StringBuilder();
        if (role != null && !role.isBlank()) {
            sb.append(role.trim());
        }
        if (skills != null && !skills.isEmpty()) {
            int limit = Math.min(4, skills.size());
            for (int i = 0; i < limit; i++) {
                sb.append(" ").append(skills.get(i).trim());
            }
        }
        return sb.toString().trim();
    }

    private String buildMatchSummary(List<JobDTO> jobs) {
        long excellent = jobs.stream().filter(j -> j.getMatchScore() >= 75).count();
        long good      = jobs.stream().filter(j -> j.getMatchScore() >= 50 && j.getMatchScore() < 75).count();
        long fair      = jobs.stream().filter(j -> j.getMatchScore() >= 25 && j.getMatchScore() < 50).count();
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