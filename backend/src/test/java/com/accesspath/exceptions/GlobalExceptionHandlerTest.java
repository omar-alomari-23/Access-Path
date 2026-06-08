package com.accesspath.exceptions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for GlobalExceptionHandler.
 * Uses standaloneSetup to wire a minimal controller directly with the advice,
 * covering the two handlers not exercised by any other test:
 *   - handleMissingHeader (MissingRequestHeaderException → 400)
 *   - handleGeneral       (unexpected Exception          → 500)
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    /** Minimal controller that deliberately triggers specific exceptions. */
    @RestController
    static class ThrowingController {

        @GetMapping("/test/missing-header")
        public String requiresHeader(
                @RequestHeader("X-Required-Header") String h) {
            return h;
        }

        @GetMapping("/test/unexpected-error")
        public String throwsGeneral() {
            throw new RuntimeException("Something went very wrong");
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── handleMissingHeader ───────────────────────────────────────────────────

    @Test
    @DisplayName("handleMissingHeader: missing required header → 400 with header name in message")
    void handleMissingHeader_missingRequiredHeader_returns400() throws Exception {
        mockMvc.perform(get("/test/missing-header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Missing required header: X-Required-Header"));
    }

    // ── handleGeneral ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("handleGeneral: unexpected exception → 500 with generic message")
    void handleGeneral_unexpectedException_returns500() throws Exception {
        mockMvc.perform(get("/test/unexpected-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message")
                        .value("An unexpected error occurred"));
    }
}
