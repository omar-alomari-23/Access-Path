package com.accesspath.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ClassificationService.
 * Pure logic — no mocks required.
 * Covers US-5: AI-assisted report classification.
 */
class ClassificationServiceTest {

    private ClassificationService classificationService;

    @BeforeEach
    void setUp() {
        classificationService = new ClassificationService();
    }

    // ── suggestCategory ──────────────────────────────────────────────────────

    @Test
    @DisplayName("suggestCategory: 'stairs' keyword → STEP_FREE_ISSUE")
    void suggestCategory_stairsKeyword_returnsStepFreeIssue() {
        String result = classificationService.suggestCategory("The stairs are inaccessible near the entrance");
        assertThat(result).isEqualTo("STEP_FREE_ISSUE");
    }

    @Test
    @DisplayName("suggestCategory: 'elevator' keyword → STEP_FREE_ISSUE")
    void suggestCategory_elevatorKeyword_returnsStepFreeIssue() {
        String result = classificationService.suggestCategory("Elevator out of service at metro station");
        assertThat(result).isEqualTo("STEP_FREE_ISSUE");
    }

    @Test
    @DisplayName("suggestCategory: 'pothole' keyword → SURFACE_HAZARD")
    void suggestCategory_potholeKeyword_returnsSurfaceHazard() {
        String result = classificationService.suggestCategory("Large pothole blocking the pedestrian path");
        assertThat(result).isEqualTo("SURFACE_HAZARD");
    }

    @Test
    @DisplayName("suggestCategory: 'slippery' keyword → SURFACE_HAZARD")
    void suggestCategory_slipperyKeyword_returnsSurfaceHazard() {
        String result = classificationService.suggestCategory("Wet and slippery tiles near the fountain");
        assertThat(result).isEqualTo("SURFACE_HAZARD");
    }

    @Test
    @DisplayName("suggestCategory: 'blocked' keyword → OBSTRUCTION")
    void suggestCategory_blockedKeyword_returnsObstruction() {
        String result = classificationService.suggestCategory("Path is blocked by scaffolding");
        assertThat(result).isEqualTo("OBSTRUCTION");
    }

    @Test
    @DisplayName("suggestCategory: 'dark' keyword → LIGHTING_ISSUE")
    void suggestCategory_darkKeyword_returnsLightingIssue() {
        String result = classificationService.suggestCategory("Very dark underpass with no lighting at night");
        assertThat(result).isEqualTo("LIGHTING_ISSUE");
    }

    @Test
    @DisplayName("suggestCategory: 'heat' keyword → HEAT_NO_SHADE")
    void suggestCategory_heatKeyword_returnsHeatNoShade() {
        String result = classificationService.suggestCategory("Extreme heat and no shade on this stretch");
        assertThat(result).isEqualTo("HEAT_NO_SHADE");
    }

    @Test
    @DisplayName("suggestCategory: uppercase input → same result as lowercase (case-insensitive)")
    void suggestCategory_uppercaseInput_returnsSameCategory() {
        String result = classificationService.suggestCategory("THE STAIRS ARE INACCESSIBLE NEAR THE ENTRANCE");
        assertThat(result).isEqualTo("STEP_FREE_ISSUE");
    }

    @Test
    @DisplayName("suggestCategory: unrecognised description → default OBSTRUCTION")
    void suggestCategory_noKeywordMatch_returnsDefault() {
        String result = classificationService.suggestCategory("Something happened here");
        assertThat(result).isEqualTo("OBSTRUCTION");
    }

    @Test
    @DisplayName("suggestCategory: null description → default OBSTRUCTION (no exception)")
    void suggestCategory_nullDescription_returnsDefault() {
        String result = classificationService.suggestCategory(null);
        assertThat(result).isEqualTo("OBSTRUCTION");
    }

    // ── suggestSeverity ──────────────────────────────────────────────────────

    @Test
    @DisplayName("suggestSeverity: 'dangerous' keyword → HIGH")
    void suggestSeverity_dangerousKeyword_returnsHigh() {
        String result = classificationService.suggestSeverity("Dangerous drop at the edge of the path");
        assertThat(result).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("suggestSeverity: 'minor' keyword → LOW")
    void suggestSeverity_minorKeyword_returnsLow() {
        String result = classificationService.suggestSeverity("Minor crack in the pavement tile");
        assertThat(result).isEqualTo("LOW");
    }

    @Test
    @DisplayName("suggestSeverity: no severity keywords → default MEDIUM")
    void suggestSeverity_noKeywordMatch_returnsMedium() {
        String result = classificationService.suggestSeverity("There is a pothole at the bus stop");
        assertThat(result).isEqualTo("MEDIUM");
    }

    // ── getConfidenceFlag ────────────────────────────────────────────────────

    @Test
    @DisplayName("getConfidenceFlag: 2+ keyword matches → true (high confidence)")
    void getConfidenceFlag_twoMatches_returnsTrue() {
        // contains both a category keyword and a severity keyword
        boolean result = classificationService.getConfidenceFlag(
                "Dangerous broken stairs near the metro elevator");
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("getConfidenceFlag: single or no keyword match → false (low confidence)")
    void getConfidenceFlag_singleMatch_returnsFalse() {
        boolean result = classificationService.getConfidenceFlag("There is scaffolding here");
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("getConfidenceFlag: empty description → false")
    void getConfidenceFlag_emptyDescription_returnsFalse() {
        boolean result = classificationService.getConfidenceFlag("");
        assertThat(result).isFalse();
    }
}
