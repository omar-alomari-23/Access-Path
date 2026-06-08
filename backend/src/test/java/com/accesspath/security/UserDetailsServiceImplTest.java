package com.accesspath.security;

import com.accesspath.models.User;
import com.accesspath.models.User.UserRole;
import com.accesspath.repositories.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for UserDetailsServiceImpl.
 * Covers loading users by email for Spring Security authentication,
 * correct authority mapping (ROLE_ prefix), and unknown-email rejection.
 */
@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    // ── loadUserByUsername ────────────────────────────────────────────────────

    @Test
    @DisplayName("loadUserByUsername: known REPORTER email → UserDetails with ROLE_REPORTER")
    void loadUserByUsername_reporterEmail_returnsCorrectUserDetails() {
        User user = User.builder()
                .userId(UUID.randomUUID())
                .email("reporter@test.com")
                .passwordHash("hashed-password")
                .role(UserRole.REPORTER)
                .build();

        when(userRepository.findByEmail("reporter@test.com")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("reporter@test.com");

        assertThat(details.getUsername()).isEqualTo("reporter@test.com");
        assertThat(details.getPassword()).isEqualTo("hashed-password");
        assertThat(details.getAuthorities()).hasSize(1);
        assertThat(details.getAuthorities().iterator().next().getAuthority())
                .isEqualTo("ROLE_REPORTER");
    }

    @Test
    @DisplayName("loadUserByUsername: known MODERATOR email → UserDetails with ROLE_MODERATOR")
    void loadUserByUsername_moderatorEmail_returnsRoleModerator() {
        User user = User.builder()
                .userId(UUID.randomUUID())
                .email("mod@test.com")
                .passwordHash("hashed-password")
                .role(UserRole.MODERATOR)
                .build();

        when(userRepository.findByEmail("mod@test.com")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("mod@test.com");

        assertThat(details.getAuthorities().iterator().next().getAuthority())
                .isEqualTo("ROLE_MODERATOR");
    }

    @Test
    @DisplayName("loadUserByUsername: known NAVIGATOR email → UserDetails with ROLE_NAVIGATOR")
    void loadUserByUsername_navigatorEmail_returnsRoleNavigator() {
        User user = User.builder()
                .userId(UUID.randomUUID())
                .email("nav@test.com")
                .passwordHash("hashed-password")
                .role(UserRole.NAVIGATOR)
                .build();

        when(userRepository.findByEmail("nav@test.com")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("nav@test.com");

        assertThat(details.getAuthorities().iterator().next().getAuthority())
                .isEqualTo("ROLE_NAVIGATOR");
    }

    @Test
    @DisplayName("loadUserByUsername: unknown email → UsernameNotFoundException")
    void loadUserByUsername_unknownEmail_throwsUsernameNotFoundException() {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                userDetailsService.loadUserByUsername("ghost@test.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost@test.com");
    }

    @Test
    @DisplayName("loadUserByUsername: password hash is preserved exactly (not re-encoded)")
    void loadUserByUsername_passwordHashPreservedAsIs() {
        String rawHash = "$2a$10$hashedpassword";
        User user = User.builder()
                .userId(UUID.randomUUID())
                .email("user@test.com")
                .passwordHash(rawHash)
                .role(UserRole.REPORTER)
                .build();

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("user@test.com");

        assertThat(details.getPassword()).isEqualTo(rawHash);
    }
}
