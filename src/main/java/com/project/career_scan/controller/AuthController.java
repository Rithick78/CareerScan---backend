package com.project.career_scan.controller;

import com.project.career_scan.config.GuestConfig;
import com.project.career_scan.dto.LoginRequest;
import com.project.career_scan.dto.LoginResponse;
import com.project.career_scan.dto.RegisterRequest;
import com.project.career_scan.dto.RegisterResponse;
import com.project.career_scan.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // POST http://localhost:8080/api/auth/register
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // POST http://localhost:8080/api/auth/login
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {

        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/guest")
    public ResponseEntity<LoginResponse> guestLogin() {
        LoginRequest guestRequest = new LoginRequest();
        guestRequest.setEmail(GuestConfig.GUEST_EMAIL);
        guestRequest.setPassword(GuestConfig.GUEST_PASSWORD);

        LoginResponse response = authService.login(guestRequest);
        return ResponseEntity.ok(response);
    }
}
