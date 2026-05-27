package com.project.career_scan.exception;

// Thrown when we can't find a user by email or id
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String email) {
        super("User not found with email: " + email);
    }

    public UserNotFoundException(Long id) {
        super("User not found with id: " + id);
    }
}
