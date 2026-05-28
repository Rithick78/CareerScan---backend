package com.project.career_scan.exception;

// Thrown when JSearch API call fails
// e.g. wrong API key, rate limit, RapidAPI server down
public class JobSearchException extends RuntimeException {

    public JobSearchException(String message) {
        super(message);
    }

    public JobSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
