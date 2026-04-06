package com.loganalyzer.controller;

import com.loganalyzer.dto.Dtos;
import com.loganalyzer.model.User;
import com.loganalyzer.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/**
 * Phase 2 – Login API
 * POST /api/auth/login   — authenticate a user
 * POST /api/auth/register — create a new user (dev convenience)
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<Dtos.ApiResponse<Dtos.LoginResponse>> login(
            @RequestBody Dtos.LoginRequest req) {

        return userRepo.findByUsername(req.getUsername())
                .filter(u -> passwordEncoder.matches(req.getPassword(), u.getPassword()))
                .filter(User::isEnabled)
                .map(u -> {
                    log.info("User '{}' logged in successfully", u.getUsername());
                    Dtos.LoginResponse resp = Dtos.LoginResponse.builder()
                            .success(true)
                            .message("Login successful")
                            .username(u.getUsername())
                            .role(u.getRole())
                            .build();
                    return ResponseEntity.ok(Dtos.ApiResponse.ok(resp));
                })
                .orElseGet(() -> {
                    log.warn("Failed login attempt for username '{}'", req.getUsername());
                    return ResponseEntity.status(401)
                            .body(Dtos.ApiResponse.error("Invalid username or password"));
                });
    }

    @PostMapping("/register")
    public ResponseEntity<Dtos.ApiResponse<String>> register(
            @RequestBody Dtos.LoginRequest req) {

        if (userRepo.existsByUsername(req.getUsername())) {
            return ResponseEntity.badRequest()
                    .body(Dtos.ApiResponse.error("Username already exists"));
        }

        User user = User.builder()
                .username(req.getUsername())
                .password(passwordEncoder.encode(req.getPassword()))
                .role("USER")
                .build();
        userRepo.save(user);

        log.info("Registered new user '{}'", req.getUsername());
        return ResponseEntity.ok(Dtos.ApiResponse.ok("User registered successfully"));
    }
}
