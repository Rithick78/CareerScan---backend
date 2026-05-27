package com.project.career_scan.exception;

// Thrown when someone tries to register with an email that already exists
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("Email already registered: " + email);
    }
}
