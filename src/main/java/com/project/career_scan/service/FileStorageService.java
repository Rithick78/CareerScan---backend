package com.project.career_scan.service;

import com.project.career_scan.exception.FileStorageException;
import com.project.career_scan.exception.InvalidFileException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    // Max file size: 5MB in bytes
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    public String saveFile(MultipartFile file, String userEmail) {

        validateFile(file);

        try {
            String safeEmail = userEmail.replace("@", "_").replace(".", "_");
            Path userUploadPath = Paths.get(uploadDir, safeEmail);
            Files.createDirectories(userUploadPath);

            String originalFileName = file.getOriginalFilename();
            String uniqueFileName = UUID.randomUUID() + "-" + originalFileName;

            Path targetPath = userUploadPath.resolve(uniqueFileName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            log.info("File saved successfully: {}", targetPath);

            return targetPath.toString();

        } catch (IOException ex) {
            log.error("Failed to save file for user: {}", userEmail, ex);
            throw new FileStorageException("Could not save file. Please try again.", ex);
        }
    }

    private void validateFile(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Please select a file to upload.");
        }

        String contentType = file.getContentType();
        String originalName = file.getOriginalFilename();

        boolean isPdf = "application/pdf".equals(contentType)
                || (originalName != null && originalName.toLowerCase().endsWith(".pdf"));

        if (!isPdf) {
            throw new InvalidFileException(
                    "Only PDF files are allowed. You uploaded: " + contentType);
        }

        // file must be under 5MB
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidFileException(
                    "File size " + (file.getSize() / 1024 / 1024) + "MB exceeds 5MB limit.");
        }

        log.info("File validation passed: name={}, size={}KB, type={}",
                originalName, file.getSize() / 1024, contentType);
    }
}

