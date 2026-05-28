package com.project.career_scan.service;

import com.project.career_scan.exception.PdfParseException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Service
@Slf4j
public class ResumeParserService {

    private static final int MIN_CHARACTERS = 50;

    public String extractTextFromPdf(String filePath) {

        log.info("PDFBox extraction started: {}", filePath);

        File pdfFile = new File(filePath);

        // Check file exists on disk
        if (!pdfFile.exists()) {
            throw new PdfParseException(
                    "PDF file not found on server. File may have failed to save. " +
                            "Path: " + filePath);
        }

        // Check file is readable
        if (!pdfFile.canRead()) {
            throw new PdfParseException(
                    "Server cannot read the PDF file. Check file permissions.");
        }

        // Check file is not 0 bytes
        if (pdfFile.length() == 0) {
            throw new PdfParseException(
                    "The uploaded PDF file is empty (0 bytes). Please upload a valid resume.");
        }

        try (PDDocument document = Loader.loadPDF(pdfFile)) {

            int pageCount = document.getNumberOfPages();
            log.info("PDF loaded. Pages: {}", pageCount);

            if (pageCount == 0) {
                throw new PdfParseException(
                        "The PDF has 0 pages. Please upload a valid resume PDF.");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            String rawText = stripper.getText(document);

            if (rawText == null || rawText.trim().length() < MIN_CHARACTERS) {
                throw new PdfParseException(
                        "Could not extract readable text from this PDF. " +
                                "It appears to be a scanned image or image-based PDF. " +
                                "Please upload a PDF created from a Word document or text editor.");
            }

            String cleaned = cleanText(rawText);

            log.info("PDFBox extraction complete. {} characters extracted from {} pages",
                    cleaned.length(), pageCount);

            log.debug("=== EXTRACTED TEXT PREVIEW ===");
            log.debug(cleaned.substring(0, Math.min(500, cleaned.length())));
            log.debug("=== END PREVIEW ===");

            return cleaned;

        } catch (PdfParseException ex) {
            throw ex;
        } catch (IOException ex) {
            log.error("IOException reading PDF: {}", ex.getMessage());
            throw new PdfParseException(
                    "Failed to read PDF. The file may be corrupted or password-protected. " +
                            "Please upload an unprotected PDF.", ex);
        }
    }

    private String cleanText(String raw) {
        if (raw == null) return "";
        return raw
                .replaceAll("(?m)^\\s*$[\n\r]{2,}", "\n\n")
                .replaceAll("[ \\t]+", " ")
                .trim();
    }
}