package com.project.career_scan.exception;

// Thrown when login fails due to wrong password
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
