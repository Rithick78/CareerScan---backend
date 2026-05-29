package com.project.career_scan.service;

import com.project.career_scan.dto.ParsedResumeData;
import com.project.career_scan.exception.AiParseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
@Slf4j
public class GroqService {

    // Injected from RestTemplateConfig has 30s timeout built in
    private final RestTemplate restTemplate;

    @Value("${groq.api.key}")
    private String groqApiKey;

    @Value("${groq.api.url}")
    private String groqApiUrl;

    @Value("${groq.model}")
    private String groqModel;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_RETRIES = 2;

    private static final int MIN_TEXT_LENGTH = 100;

    public ParsedResumeData parseResume(String resumeText) {

        log.info("Groq AI parse requested. Text length: {} chars", resumeText.length());

        validateResumeText(resumeText);

        String prompt = buildPrompt(resumeText);

        String rawResponse = callGroqWithRetry(prompt);

        ParsedResumeData result = parseJsonResponse(rawResponse);

        log.info("Groq parse complete. Role: {}, Skills: {}",
                result.getDetectedRole(),
                result.getSkills() != null ? result.getSkills().size() + " found" : "none");

        return result;
    }

    private void validateResumeText(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new AiParseException(
                    "Resume text is empty. The PDF may be image-based or corrupted. " +
                            "Please upload a text-based PDF.");
        }
        if (text.trim().length() < MIN_TEXT_LENGTH) {
            throw new AiParseException(
                    "Resume text is too short (" + text.trim().length() + " characters). " +
                            "Please upload a complete resume PDF.");
        }
    }

    private String callGroqWithRetry(String prompt) {

        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRIES) {
            attempt++;
            log.info("Groq API call attempt {}/{}", attempt, MAX_RETRIES);

            try {
                return callGroqApi(prompt);

            } catch (AiParseException ex) {
                // Client errors (401, 400) — don't retry, fail immediately
                if (ex.getMessage().contains("Invalid Groq API key") ||
                        ex.getMessage().contains("Bad request")) {
                    throw ex;
                }
                // Server errors — retry
                lastException = ex;
                log.warn("Attempt {} failed: {}. Retrying...", attempt, ex.getMessage());

                if (attempt < MAX_RETRIES) {
                    try {
                        // Wait 2 seconds before retry
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        throw new AiParseException(
                "Groq AI service failed after " + MAX_RETRIES + " attempts. " +
                        "Please try again in a moment. Error: " +
                        (lastException != null ? lastException.getMessage() : "Unknown"));
    }

    private String callGroqApi(String prompt) {

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(groqApiKey);

            Map<String, Object> requestBody = Map.of(
                    "model", groqModel,
                    "messages", List.of(
                            Map.of("role", "user", "content", prompt)
                    ),
                    "temperature", 0.1,
                    "max_tokens", 1000
            );

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    groqApiUrl,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            return extractContent(response.getBody());

        } catch (ResourceAccessException ex) {
            log.error("Groq API timeout after 30 seconds");
            throw new AiParseException(
                    "AI service timed out. Groq API did not respond in 30 seconds. " +
                            "Please try again.");

        } catch (HttpClientErrorException ex) {
            log.error("Groq client error: {} - {}", ex.getStatusCode(),
                    ex.getResponseBodyAsString());

            if (ex.getStatusCode().value() == 401) {
                throw new AiParseException(
                        "Invalid Groq API key. Go to https://console.groq.com, " +
                                "create a new API key and update application.properties.");
            }
            if (ex.getStatusCode().value() == 429) {
                throw new AiParseException(
                        "Groq API rate limit hit. Wait 1 minute and try again. " +
                                "Free tier allows 30 requests/minute.");
            }
            if (ex.getStatusCode().value() == 400) {
                throw new AiParseException(
                        "Bad request to Groq API. Resume text may contain invalid characters.");
            }
            throw new AiParseException("Groq API error: " + ex.getStatusCode());

        } catch (HttpServerErrorException ex) {
            log.error("Groq server error: {}", ex.getStatusCode());
            throw new AiParseException(
                    "Groq server error (" + ex.getStatusCode() + "). Retrying...");

        } catch (AiParseException ex) {
            throw ex;

        } catch (Exception ex) {
            log.error("Unexpected error calling Groq", ex);
            throw new AiParseException(
                    "Could not connect to AI service: " + ex.getMessage(), ex);
        }
    }

    private String extractContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root
                    .path("choices")
                    .get(0)
                    .path("message")
                    .path("content")
                    .asText();

            log.info("=== GROQ AI RAW RESPONSE ===");
            log.info(content);
            log.info("=== END GROQ RESPONSE ===");

            return content;

        } catch (Exception ex) {
            log.error("Cannot read Groq response: {}", responseBody);
            throw new AiParseException("Could not read AI response format.");
        }
    }

    private ParsedResumeData parseJsonResponse(String rawJson) {
        try {
            String clean = rawJson
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            int start = clean.indexOf('{');
            int end   = clean.lastIndexOf('}');

            if (start == -1 || end == -1 || start >= end) {
                throw new AiParseException(
                        "AI did not return valid JSON. Raw: " + rawJson);
            }

            clean = clean.substring(start, end + 1);

            ParsedResumeData data = objectMapper.readValue(clean, ParsedResumeData.class);

            if (data.getSkills() == null)         data.setSkills(new ArrayList<>());
            if (data.getDetectedRole() == null ||
                    data.getDetectedRole().isBlank())  data.setDetectedRole("Software Developer");
            if (data.getExperience() == null)      data.setExperience("Not specified");
            if (data.getCity() == null)            data.setCity("Not specified");
            if (data.getSummary() == null)         data.setSummary("");
            if (data.getEmail() == null)           data.setEmail("");
            if (data.getPhone() == null)           data.setPhone("");

            return data;

        } catch (AiParseException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("JSON parse failed. Raw: {}", rawJson);
            throw new AiParseException(
                    "AI returned unexpected format. Please try uploading again.", ex);
        }
    }

    private String buildPrompt(String resumeText) {
        return """
            You are a resume parser. Extract information from the resume text below.
            
            Return ONLY a valid JSON object with these exact fields.
            No extra text. No explanation. No markdown. No code blocks.
            
            {
              "detectedRole": "primary job role of the person",
              "skills": ["skill1", "skill2", "skill3"],
              "experience": "total years e.g. '2 years' or 'Fresher'",
              "city": "city or location, or 'Not specified'",
              "summary": "one sentence professional summary",
              "email": "email from resume or empty string",
              "phone": "phone number or empty string"
            }
            
            Rules:
            - Return ONLY the JSON. Nothing before or after.
            - skills must be a JSON array of strings
            - If field not found, use empty string or empty array
            
            Resume Text:
            """ + resumeText;
    }
}


