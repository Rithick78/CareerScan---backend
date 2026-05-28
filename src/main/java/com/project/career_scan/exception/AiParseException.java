package com.project.career_scan.exception;

// Thrown when Groq AI API call fails
// or when AI response cannot be parsed as JSON
public class AiParseException extends RuntimeException {

    public AiParseException(String message) {
        super(message);
    }

    public AiParseException(String message, Throwable cause) {
        super(message, cause);
    }
}