package com.project.career_scan.service;

import com.project.career_scan.dto.ParsedResumeData;
import com.project.career_scan.dto.ResumeUploadResponse;
import com.project.career_scan.exception.AiParseException;
import com.project.career_scan.exception.FileStorageException;
import com.project.career_scan.exception.PdfParseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

// DAY 10 — FINAL HARDENED FLOW
// Every step wrapped with clear error handling
// If any step fails → clean error returned to frontend
// No silent failures, no hanging requests

@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeService {

    private final FileStorageService fileStorageService;
    private final ResumeParserService resumeParserService;
    private final GroqService groqService;
    private final ResumeDataService resumeDataService;

    public ResumeUploadResponse uploadResume(MultipartFile file, String userEmail) {

        log.info("========================================");
        log.info("CAREERSCAN RESUME FLOW STARTED");
        log.info("User   : {}", userEmail);
        log.info("File   : {}", file.getOriginalFilename());
        log.info("Size   : {} KB", file.getSize() / 1024);
        log.info("========================================");

        // -----------------------------------------------
        // STEP 1 — Save PDF to disk
        // Fails if: file is not PDF, over 5MB, disk error
        // -----------------------------------------------
        String savedFilePath;
        try {
            savedFilePath = fileStorageService.saveFile(file, userEmail);
            log.info("STEP 1 ✅ File saved → {}", savedFilePath);
        } catch (FileStorageException ex) {
            log.error("STEP 1 ❌ File save failed: {}", ex.getMessage());
            throw ex; // GlobalExceptionHandler handles this → 500
        }

        // -----------------------------------------------
        // STEP 2 — Extract text from PDF using PDFBox
        // Fails if: PDF is scanned image, corrupted, 0 pages
        // -----------------------------------------------
        String extractedText;
        try {
            extractedText = resumeParserService.extractTextFromPdf(savedFilePath);
            log.info("STEP 2 ✅ Text extracted → {} characters", extractedText.length());
        } catch (PdfParseException ex) {
            log.error("STEP 2 ❌ PDF text extraction failed: {}", ex.getMessage());
            throw ex; // GlobalExceptionHandler handles → 422
        }

        // -----------------------------------------------
        // STEP 3 — Send to Groq AI, parse resume
        // Fails if: API key wrong, timeout, Groq server down
        // Has retry logic inside GroqService (2 attempts)
        // -----------------------------------------------
        ParsedResumeData parsedData;
        try {
            parsedData = groqService.parseResume(extractedText);
            log.info("STEP 3 ✅ AI parse complete → Role: {}, Skills: {}",
                    parsedData.getDetectedRole(),
                    parsedData.getSkills() != null ? parsedData.getSkills().size() : 0);
        } catch (AiParseException ex) {
            log.error("STEP 3 ❌ Groq AI failed: {}", ex.getMessage());
            throw ex; // GlobalExceptionHandler handles → 502
        }

        // -----------------------------------------------
        // STEP 4 — Save parsed data to MySQL
        // Fails if: DB connection down, user not found
        // -----------------------------------------------
        ParsedResumeData.ParsedResumeResponse savedResponse;
        try {
            savedResponse = resumeDataService.saveResumeData(
                    parsedData,
                    userEmail,
                    savedFilePath,
                    file.getOriginalFilename()
            );
            log.info("STEP 4 ✅ Saved to DB → id: {}", savedResponse.getId());
        } catch (Exception ex) {
            log.error("STEP 4 ❌ DB save failed: {}", ex.getMessage());
            throw new RuntimeException(
                    "Resume was parsed successfully but could not be saved to database. " +
                            "Please try again.", ex);
        }

        log.info("========================================");
        log.info("CAREERSCAN RESUME FLOW COMPLETE ✅");
        log.info("DB id  : {}", savedResponse.getId());
        log.info("Role   : {}", savedResponse.getDetectedRole());
        log.info("Skills : {}", savedResponse.getSkills());
        log.info("City   : {}", savedResponse.getCity());
        log.info("========================================");

        return new ResumeUploadResponse(
                "Resume uploaded, AI-parsed, and saved successfully!",
                file.getOriginalFilename(),
                savedFilePath,
                file.getSize() / 1024,
                savedResponse
        );
    }
}