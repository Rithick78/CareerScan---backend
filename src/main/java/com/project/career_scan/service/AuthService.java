package com.project.career_scan.service;

import com.project.career_scan.dto.LoginRequest;
import com.project.career_scan.dto.LoginResponse;
import com.project.career_scan.dto.RegisterRequest;
import com.project.career_scan.dto.RegisterResponse;
import com.project.career_scan.entity.User;
import com.project.career_scan.exception.EmailAlreadyExistsException;
import com.project.career_scan.exception.InvalidCredentialsException;
import com.project.career_scan.repository.UserRepository;
import com.project.career_scan.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public RegisterResponse register(RegisterRequest request) {

        log.info("Register attempt for email: {}", request.getEmail());

        // Check duplicate email — throws exception if exists
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Email already exists: {}", request.getEmail());
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        // Build user
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        userRepository.save(user);

        log.info("User registered successfully: {}", user.getEmail());

        return new RegisterResponse("User registered successfully!", user.getEmail());
    }

    public LoginResponse login(LoginRequest request) {

        log.info("Login attempt for email: {}", request.getEmail());

        // Find user — throws exception if not found
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("User not found: {}", request.getEmail());
                    return new InvalidCredentialsException(); // don't reveal "user not found" for security
                });

        // Check password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Wrong password for: {}", request.getEmail());
            throw new InvalidCredentialsException();
        }

        // Generate JWT
        String token = jwtUtil.generateToken(user.getEmail());

        log.info("Login successful for: {}", user.getEmail());

        return new LoginResponse(token, user.getEmail(), user.getName(), "Login successful!");
    }
}