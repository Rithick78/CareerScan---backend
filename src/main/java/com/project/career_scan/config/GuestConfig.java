package com.project.career_scan.config;

import com.project.career_scan.entity.User;
import com.project.career_scan.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;


@Configuration
@RequiredArgsConstructor
@Slf4j
public class GuestConfig {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Fixed guest credentials — same every time
    public static final String GUEST_EMAIL    = "guest@careerscan.com";
    public static final String GUEST_PASSWORD = "guest123";
    public static final String GUEST_NAME     = "Guest User";

    @Bean
    public CommandLineRunner createGuestUser() {
        return args -> {
            // Only create if guest does not already exist
            if (!userRepository.existsByEmail(GUEST_EMAIL)) {
                User guest = new User();
                guest.setName(GUEST_NAME);
                guest.setEmail(GUEST_EMAIL);
                guest.setPassword(passwordEncoder.encode(GUEST_PASSWORD));
                userRepository.save(guest);
                log.info("Guest user created: {}", GUEST_EMAIL);
            } else {
                log.info("Guest user already exists: {}", GUEST_EMAIL);
            }
        };
    }
}
