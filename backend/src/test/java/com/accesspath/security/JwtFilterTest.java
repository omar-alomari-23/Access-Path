package com.accesspath.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtFilter (OncePerRequestFilter).
 * Covers: missing header (pass-through), invalid/expired token (401),
 * valid token (authentication set in SecurityContext), and already-authenticated
 * request (no double-auth).
 */
@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private UserDetailsServiceImpl userDetailsService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @InjectMocks
    private JwtFilter jwtFilter;

    @BeforeEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void resetSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── no Authorization header ───────────────────────────────────────────────

    @Test
    @DisplayName("doFilterInternal: no Authorization header → passes to next filter, no auth set")
    void doFilterInternal_noHeader_passesThrough() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        jwtFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtUtil, userDetailsService);
    }

    @Test
    @DisplayName("doFilterInternal: header without 'Bearer ' prefix → passes through")
    void doFilterInternal_headerWithoutBearerPrefix_passesThrough() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        jwtFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtUtil, userDetailsService);
    }

    // ── invalid / expired token ───────────────────────────────────────────────

    @Test
    @DisplayName("doFilterInternal: invalid token → 401 response, filter chain not continued")
    void doFilterInternal_invalidToken_returns401() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer bad.token.here");
        when(jwtUtil.isTokenValid("bad.token.here")).thenReturn(false);

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        jwtFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("doFilterInternal: expired token → 401 response")
    void doFilterInternal_expiredToken_returns401() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer expired.token");
        when(jwtUtil.isTokenValid("expired.token")).thenReturn(false);

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        jwtFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(userDetailsService);
    }

    // ── valid token ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("doFilterInternal: valid token → authentication set in SecurityContext")
    void doFilterInternal_validToken_setsAuthentication() throws Exception {
        String token = "valid.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.isTokenValid(token)).thenReturn(true);
        when(jwtUtil.extractEmail(token)).thenReturn("user@test.com");

        UserDetails userDetails = new User(
                "user@test.com",
                "hashed",
                List.of(new SimpleGrantedAuthority("ROLE_REPORTER"))
        );
        when(userDetailsService.loadUserByUsername("user@test.com"))
                .thenReturn(userDetails);

        jwtFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("user@test.com");
        assertThat(SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().iterator().next().getAuthority())
                .isEqualTo("ROLE_REPORTER");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("doFilterInternal: valid token → filter chain continues after auth")
    void doFilterInternal_validToken_filterChainContinues() throws Exception {
        String token = "valid.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.isTokenValid(token)).thenReturn(true);
        when(jwtUtil.extractEmail(token)).thenReturn("mod@test.com");

        UserDetails userDetails = new User(
                "mod@test.com",
                "hashed",
                List.of(new SimpleGrantedAuthority("ROLE_MODERATOR"))
        );
        when(userDetailsService.loadUserByUsername("mod@test.com"))
                .thenReturn(userDetails);

        jwtFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    // ── loadUserByUsername throws ─────────────────────────────────────────────

    @Test
    @DisplayName("doFilterInternal: valid token but user not found in DB → exception propagates")
    void doFilterInternal_userNotFoundInDb_exceptionPropagates() throws Exception {
        String token = "valid.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.isTokenValid(token)).thenReturn(true);
        when(jwtUtil.extractEmail(token)).thenReturn("ghost@test.com");
        when(userDetailsService.loadUserByUsername("ghost@test.com"))
                .thenThrow(new UsernameNotFoundException("User not found"));

        org.junit.jupiter.api.Assertions.assertThrows(UsernameNotFoundException.class,
                () -> jwtFilter.doFilterInternal(request, response, filterChain));

        verify(filterChain, never()).doFilter(any(), any());
    }

    // ── already authenticated ─────────────────────────────────────────────────

    @Test
    @DisplayName("doFilterInternal: valid token but authentication already present → skips loading")
    void doFilterInternal_alreadyAuthenticated_skipsUserDetailsLoad() throws Exception {
        String token = "valid.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.isTokenValid(token)).thenReturn(true);
        when(jwtUtil.extractEmail(token)).thenReturn("user@test.com");

        // Pre-populate the SecurityContext with an existing authentication
        UserDetails existing = new User(
                "user@test.com", "hashed",
                List.of(new SimpleGrantedAuthority("ROLE_REPORTER"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        existing, null, existing.getAuthorities())
        );

        jwtFilter.doFilterInternal(request, response, filterChain);

        // userDetailsService.loadUserByUsername should NOT be called again
        verifyNoInteractions(userDetailsService);
        verify(filterChain).doFilter(request, response);
    }
}
