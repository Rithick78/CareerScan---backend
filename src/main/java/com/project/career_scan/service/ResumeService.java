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

@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeService {

    private final FileStorageService fileStorageService;
    private final ResumeParserService resumeParserService;
    private final GroqService groqService;
    private final ResumeDataService resumeDataService;

    public ResumeUploadResponse uploadResume(MultipartFile file, String userEmail) {

        String savedFilePath;
        try {
            savedFilePath = fileStorageService.saveFile(file, userEmail);
        } catch (FileStorageException ex) {
            throw ex; // GlobalExceptionHandler handles this → 500
        }

        String extractedText;
        try {
            extractedText = resumeParserService.extractTextFromPdf(savedFilePath);
        } catch (PdfParseException ex) {
            throw ex; // GlobalExceptionHandler handles → 422
        }

        ParsedResumeData parsedData;
        try {
            parsedData = groqService.parseResume(extractedText);
             log.info("AI parse complete → Role: {}, Skills: {}",
                    parsedData.getDetectedRole(),
                    parsedData.getSkills() != null ? parsedData.getSkills().size() : 0);
        } catch (AiParseException ex) {
            throw ex; // GlobalExceptionHandler handles → 502
        }

        ParsedResumeData.ParsedResumeResponse savedResponse;
        try {
            savedResponse = resumeDataService.saveResumeData(
                    parsedData,
                    userEmail,
                    savedFilePath,
                    file.getOriginalFilename()
            );
        } catch (Exception ex) {
            throw new RuntimeException(
                    "Resume was parsed successfully but could not be saved to database. " +
                            "Please try again.", ex);
        }

        return new ResumeUploadResponse(
                "Resume uploaded, AI-parsed, and saved successfully!",
                file.getOriginalFilename(),
                savedFilePath,
                file.getSize() / 1024,
                savedResponse
        );
    }
}