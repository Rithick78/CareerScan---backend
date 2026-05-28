package com.project.career_scan.exception;

// Thrown when PDFBox fails to read or extract text from the PDF
public class PdfParseException extends RuntimeException {

    public PdfParseException(String message) {
        super(message);
    }

    public PdfParseException(String message, Throwable cause) {
        super(message, cause);
    }
}

