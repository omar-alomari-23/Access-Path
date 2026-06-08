package com.accesspath.services;

import com.accesspath.dto.UserDTO;
import com.accesspath.exceptions.ResourceNotFoundException;
import com.accesspath.models.User;
import com.accesspath.models.User.UserRole;
import com.accesspath.repositories.UserRepository;
import com.accesspath.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserService.
 * Covers US-1 (registration) and US-2 (login).
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks
    private UserService userService;

    // ── register ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register: new email + REPORTER role → saves user and returns token")
    void register_newReporterEmail_savesAndReturnsToken() {
        UserDTO.RegisterRequest request = new UserDTO.RegisterRequest();
        request.setEmail("reporter@test.com");
        request.setPassword("Password1!");
        request.setRole(UserRole.REPORTER);

        User saved = User.builder()
                .userId(UUID.randomUUID())
                .email("reporter@test.com")
                .passwordHash("hashed")
                .role(UserRole.REPORTER)
                .build();

        when(userRepository.existsByEmail("reporter@test.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1!")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(jwtUtil.generateToken(any(), anyString(), anyString())).thenReturn("jwt-token");

        UserDTO.AuthResponse response = userService.register(request);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getEmail()).isEqualTo("reporter@test.com");
        assertThat(response.getRole()).isEqualTo(UserRole.REPORTER);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("register: duplicate email → IllegalStateException")
    void register_duplicateEmail_throwsIllegalStateException() {
        UserDTO.RegisterRequest request = new UserDTO.RegisterRequest();
        request.setEmail("existing@test.com");
        request.setPassword("Password1!");
        request.setRole(UserRole.NAVIGATOR);

        when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register: MODERATOR role self-assignment → IllegalStateException")
    void register_moderatorRole_throwsIllegalStateException() {
        UserDTO.RegisterRequest request = new UserDTO.RegisterRequest();
        request.setEmail("mod@test.com");
        request.setPassword("Password1!");
        request.setRole(UserRole.MODERATOR);

        when(userRepository.existsByEmail("mod@test.com")).thenReturn(false);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MODERATOR");

        verify(userRepository, never()).save(any());
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login: valid credentials → returns token")
    void login_validCredentials_returnsToken() {
        UserDTO.LoginRequest request = new UserDTO.LoginRequest();
        request.setEmail("reporter@test.com");
        request.setPassword("Password1!");

        User user = User.builder()
                .userId(UUID.randomUUID())
                .email("reporter@test.com")
                .passwordHash("hashed")
                .role(UserRole.REPORTER)
                .build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null); // authenticate() returns Authentication; null = success for this test
        when(userRepository.findByEmail("reporter@test.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(any(), anyString(), anyString())).thenReturn("jwt-token");

        UserDTO.AuthResponse response = userService.login(request);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getEmail()).isEqualTo("reporter@test.com");
    }

    @Test
    @DisplayName("login: wrong password → BadCredentialsException from AuthenticationManager")
    void login_wrongPassword_throwsBadCredentialsException() {
        UserDTO.LoginRequest request = new UserDTO.LoginRequest();
        request.setEmail("reporter@test.com");
        request.setPassword("wrongpassword");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("login: email not found after auth → ResourceNotFoundException")
    void login_userNotFound_throwsResourceNotFoundException() {
        UserDTO.LoginRequest request = new UserDTO.LoginRequest();
        request.setEmail("ghost@test.com");
        request.setPassword("Password1!");

        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── register: role edge cases ─────────────────────────────────────────────

    @Test
    @DisplayName("register: NAVIGATOR role → allowed, saves and returns token")
    void register_navigatorRole_savesAndReturnsToken() {
        UserDTO.RegisterRequest request = new UserDTO.RegisterRequest();
        request.setEmail("nav@test.com");
        request.setPassword("Password1!");
        request.setRole(UserRole.NAVIGATOR);

        User saved = User.builder()
                .userId(UUID.randomUUID())
                .email("nav@test.com")
                .passwordHash("hashed")
                .role(UserRole.NAVIGATOR)
                .build();

        when(userRepository.existsByEmail("nav@test.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1!")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(jwtUtil.generateToken(any(), anyString(), anyString())).thenReturn("jwt-token");

        UserDTO.AuthResponse response = userService.register(request);

        assertThat(response.getRole()).isEqualTo(UserRole.NAVIGATOR);
        assertThat(response.getToken()).isEqualTo("jwt-token");
    }

    // ── getUserById ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("getUserById: existing user → returns UserDTO.Response with correct fields")
    void getUserById_existingUser_returnsResponse() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .userId(userId)
                .email("alice@test.com")
                .passwordHash("hashed")
                .role(UserRole.REPORTER)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserDTO.Response response = userService.getUserById(userId);

        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getEmail()).isEqualTo("alice@test.com");
        assertThat(response.getRole()).isEqualTo(UserRole.REPORTER);
    }

    @Test
    @DisplayName("getUserById: unknown user ID → ResourceNotFoundException")
    void getUserById_unknownUser_throwsResourceNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(unknownId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
