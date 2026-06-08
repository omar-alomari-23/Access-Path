package com.accesspath.controllers;

import com.accesspath.dto.UserDTO;
import com.accesspath.exceptions.UnauthorizedException;
import com.accesspath.models.User.UserRole;
import com.accesspath.security.JwtUtil;
import com.accesspath.security.UserDetailsServiceImpl;
import com.accesspath.services.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer unit tests for AuthController.
 * Covers registration and login HTTP behaviour including all validation
 * boundaries: missing fields, invalid email format, blank values, wrong
 * credentials, and duplicate email rejection.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    // ── POST /api/auth/register ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /register: valid request → 201 with token and role")
    void register_validRequest_returns201WithToken() throws Exception {
        UserDTO.AuthResponse response = UserDTO.AuthResponse.builder()
                .token("eyJhbGci.test")
                .userId(UUID.randomUUID())
                .email("alice@test.com")
                .role(UserRole.REPORTER)
                .build();

        when(userService.register(any(UserDTO.RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@test.com\",\"password\":\"SecurePass1!\",\"role\":\"REPORTER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.email").value("alice@test.com"))
                .andExpect(jsonPath("$.role").value("REPORTER"));
    }

    @Test
    @DisplayName("POST /register: missing email field → 400 Bad Request")
    void register_missingEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"SecurePass1!\",\"role\":\"REPORTER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("POST /register: blank email → 400 Bad Request")
    void register_blankEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"SecurePass1!\",\"role\":\"REPORTER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("POST /register: invalid email format (no @) → 400 Bad Request")
    void register_invalidEmailFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"notanemail\",\"password\":\"SecurePass1!\",\"role\":\"REPORTER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("POST /register: missing password field → 400 Bad Request")
    void register_missingPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@test.com\",\"role\":\"REPORTER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("POST /register: blank password → 400 Bad Request")
    void register_blankPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@test.com\",\"password\":\"\",\"role\":\"REPORTER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("POST /register: missing role → 400 Bad Request")
    void register_missingRole_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@test.com\",\"password\":\"SecurePass1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("POST /register: empty body → 400 Bad Request")
    void register_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("POST /register: MODERATOR role self-assignment → 409 Conflict")
    void register_moderatorRole_returns409() throws Exception {
        when(userService.register(any())).thenThrow(
                new IllegalStateException("Cannot self-assign MODERATOR role"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@test.com\",\"password\":\"SecurePass1!\",\"role\":\"MODERATOR\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("POST /register: duplicate email → 409 Conflict")
    void register_duplicateEmail_returns409() throws Exception {
        when(userService.register(any())).thenThrow(
                new IllegalStateException("Email already registered"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@test.com\",\"password\":\"SecurePass1!\",\"role\":\"REPORTER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // ── POST /api/auth/login ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /login: valid credentials → 200 with token")
    void login_validCredentials_returns200WithToken() throws Exception {
        UserDTO.AuthResponse response = UserDTO.AuthResponse.builder()
                .token("eyJhbGci.test")
                .userId(UUID.randomUUID())
                .email("alice@test.com")
                .role(UserRole.REPORTER)
                .build();

        when(userService.login(any(UserDTO.LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@test.com\",\"password\":\"SecurePass1!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.role").value("REPORTER"));
    }

    @Test
    @DisplayName("POST /login: missing email field → 400 Bad Request")
    void login_missingEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"SecurePass1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("POST /login: blank email → 400 Bad Request")
    void login_blankEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"SecurePass1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("POST /login: invalid email format → 400 Bad Request")
    void login_invalidEmailFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"notanemail\",\"password\":\"SecurePass1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("POST /login: missing password field → 400 Bad Request")
    void login_missingPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@test.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("POST /login: blank password → 400 Bad Request")
    void login_blankPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@test.com\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("POST /login: empty body → 400 Bad Request")
    void login_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("POST /login: wrong password → 401 Unauthorized")
    void login_wrongPassword_returns401() throws Exception {
        when(userService.login(any())).thenThrow(
                new UnauthorizedException("Invalid credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@test.com\",\"password\":\"WrongPass!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("POST /login: unknown email → 401 Unauthorized")
    void login_unknownEmail_returns401() throws Exception {
        when(userService.login(any())).thenThrow(
                new UnauthorizedException("Invalid credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ghost@test.com\",\"password\":\"pass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
