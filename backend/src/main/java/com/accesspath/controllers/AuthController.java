package com.accesspath.controllers;

import com.accesspath.dto.UserDTO;
import com.accesspath.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<UserDTO.AuthResponse> register(
            @Valid @RequestBody UserDTO.RegisterRequest request) {
        log.info("Register request for email: {}", request.getEmail());
        UserDTO.AuthResponse response = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<UserDTO.AuthResponse> login(
            @Valid @RequestBody UserDTO.LoginRequest request) {
        log.info("Login request for email: {}", request.getEmail());
        UserDTO.AuthResponse response = userService.login(request);
        return ResponseEntity.ok(response);
    }
}
