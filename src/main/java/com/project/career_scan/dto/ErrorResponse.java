package com.project.career_scan.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ErrorResponse {

    private int status;
    private String error;
    private String message;
    private LocalDateTime timestamp;

    public ErrorResponse(int status, String error, String message) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
}
