package com.accesspath.services;

import com.accesspath.dto.UserDTO;
import com.accesspath.exceptions.ResourceNotFoundException;
import com.accesspath.models.User;
import com.accesspath.models.User.UserRole;
import com.accesspath.repositories.UserRepository;
import com.accesspath.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public UserDTO.AuthResponse register(UserDTO.RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalStateException(
                "Email already registered: " + request.getEmail()
            );
        }

        if (request.getRole() == UserRole.MODERATOR) {
            throw new IllegalStateException(
                "Cannot self-assign MODERATOR role"
            );
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .build();

        User saved = userRepository.save(user);
        log.info("Registered new user: {} with role: {}",
            saved.getEmail(), saved.getRole());

        String token = jwtUtil.generateToken(
            saved.getUserId(),
            saved.getEmail(),
            saved.getRole().name()
        );

        return UserDTO.AuthResponse.builder()
                .token(token)
                .userId(saved.getUserId())
                .email(saved.getEmail())
                .role(saved.getRole())
                .build();
    }

    public UserDTO.AuthResponse login(UserDTO.LoginRequest request) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.getEmail(),
                request.getPassword()
            )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "User", request.getEmail()
                ));

        String token = jwtUtil.generateToken(
            user.getUserId(),
            user.getEmail(),
            user.getRole().name()
        );

        return UserDTO.AuthResponse.builder()
                .token(token)
                .userId(user.getUserId())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    @Transactional(readOnly = true)
    public UserDTO.Response getUserById(java.util.UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "User", userId.toString()
                ));

        return UserDTO.Response.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
    }
}