package com.project.career_scan.exception;

// Thrown when uploaded file is not a valid PDF
// or is empty or too large
public class InvalidFileException extends RuntimeException {

    public InvalidFileException(String message) {
        super(message);
    }
}