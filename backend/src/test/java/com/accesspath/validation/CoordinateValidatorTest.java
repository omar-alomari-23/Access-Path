package com.accesspath.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CoordinateValidator.
 * Covers null safety, valid boundaries (inclusive), and out-of-range rejection
 * for both latitude [-90, 90] and longitude [-180, 180].
 */
class CoordinateValidatorTest {

    private CoordinateValidator validator;
    private ConstraintValidatorContext context;
    private ConstraintValidatorContext.ConstraintViolationBuilder builder;
    private ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext nodeBuilder;

    /** Minimal HasCoordinates implementation for testing. */
    private record Coords(Double latitude, Double longitude) implements HasCoordinates {
        @Override public Double getLatitude()  { return latitude; }
        @Override public Double getLongitude() { return longitude; }
    }

    @BeforeEach
    void setUp() {
        validator   = new CoordinateValidator();
        context     = mock(ConstraintValidatorContext.class);
        builder     = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        nodeBuilder = mock(
            ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext.class);

        // Wire mocks for the invalid-path chain:
        // context.buildConstraintViolationWithTemplate(...).addPropertyNode(...).addConstraintViolation()
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addPropertyNode(anyString())).thenReturn(nodeBuilder);
    }

    // ── null / valid input ────────────────────────────────────────────────────

    @Test
    @DisplayName("isValid: null request object → valid (skip validation)")
    void isValid_nullRequest_returnsTrue() {
        assertThat(validator.isValid(null, context)).isTrue();
    }

    @Test
    @DisplayName("isValid: valid coordinates (25.2048, 55.2708) → valid")
    void isValid_typicalCoordinates_returnsTrue() {
        assertThat(validator.isValid(new Coords(25.2048, 55.2708), context)).isTrue();
    }

    // ── null fields ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("isValid: null latitude → invalid")
    void isValid_nullLatitude_returnsFalse() {
        assertThat(validator.isValid(new Coords(null, 55.2708), context)).isFalse();
    }

    @Test
    @DisplayName("isValid: null longitude → invalid")
    void isValid_nullLongitude_returnsFalse() {
        assertThat(validator.isValid(new Coords(25.2048, null), context)).isFalse();
    }

    @Test
    @DisplayName("isValid: both null → invalid")
    void isValid_bothNull_returnsFalse() {
        assertThat(validator.isValid(new Coords(null, null), context)).isFalse();
    }

    // ── latitude boundary values ──────────────────────────────────────────────

    @Test
    @DisplayName("isValid: latitude exactly -90 (lower boundary) → valid")
    void isValid_latitudeAtLowerBoundary_returnsTrue() {
        assertThat(validator.isValid(new Coords(-90.0, 0.0), context)).isTrue();
    }

    @Test
    @DisplayName("isValid: latitude exactly 90 (upper boundary) → valid")
    void isValid_latitudeAtUpperBoundary_returnsTrue() {
        assertThat(validator.isValid(new Coords(90.0, 0.0), context)).isTrue();
    }

    @Test
    @DisplayName("isValid: latitude -90.0001 (just outside lower boundary) → invalid")
    void isValid_latitudeJustBelowLowerBoundary_returnsFalse() {
        assertThat(validator.isValid(new Coords(-90.0001, 0.0), context)).isFalse();
    }

    @Test
    @DisplayName("isValid: latitude 90.0001 (just outside upper boundary) → invalid")
    void isValid_latitudeJustAboveUpperBoundary_returnsFalse() {
        assertThat(validator.isValid(new Coords(90.0001, 0.0), context)).isFalse();
    }

    @Test
    @DisplayName("isValid: latitude -200 (far outside range) → invalid")
    void isValid_latitudeFarOutOfRange_returnsFalse() {
        assertThat(validator.isValid(new Coords(-200.0, 0.0), context)).isFalse();
    }

    // ── longitude boundary values ─────────────────────────────────────────────

    @Test
    @DisplayName("isValid: longitude exactly -180 (lower boundary) → valid")
    void isValid_longitudeAtLowerBoundary_returnsTrue() {
        assertThat(validator.isValid(new Coords(0.0, -180.0), context)).isTrue();
    }

    @Test
    @DisplayName("isValid: longitude exactly 180 (upper boundary) → valid")
    void isValid_longitudeAtUpperBoundary_returnsTrue() {
        assertThat(validator.isValid(new Coords(0.0, 180.0), context)).isTrue();
    }

    @Test
    @DisplayName("isValid: longitude -180.0001 (just outside lower boundary) → invalid")
    void isValid_longitudeJustBelowLowerBoundary_returnsFalse() {
        assertThat(validator.isValid(new Coords(0.0, -180.0001), context)).isFalse();
    }

    @Test
    @DisplayName("isValid: longitude 180.0001 (just outside upper boundary) → invalid")
    void isValid_longitudeJustAboveUpperBoundary_returnsFalse() {
        assertThat(validator.isValid(new Coords(0.0, 180.0001), context)).isFalse();
    }

    @Test
    @DisplayName("isValid: longitude 360 (far outside range) → invalid")
    void isValid_longitudeFarOutOfRange_returnsFalse() {
        assertThat(validator.isValid(new Coords(0.0, 360.0), context)).isFalse();
    }

    // ── equator / prime meridian (zero values) ────────────────────────────────

    @Test
    @DisplayName("isValid: (0, 0) equator/prime meridian → valid")
    void isValid_zeroCoordinates_returnsTrue() {
        assertThat(validator.isValid(new Coords(0.0, 0.0), context)).isTrue();
    }

    // ── NaN and Infinity values ───────────────────────────────────────────────

    @Test
    @DisplayName("isValid: NaN latitude → invalid")
    void isValid_nanLatitude_returnsFalse() {
        assertThat(validator.isValid(new Coords(Double.NaN, 0.0), context)).isFalse();
    }

    @Test
    @DisplayName("isValid: NaN longitude → invalid")
    void isValid_nanLongitude_returnsFalse() {
        assertThat(validator.isValid(new Coords(0.0, Double.NaN), context)).isFalse();
    }

    @Test
    @DisplayName("isValid: positive Infinity latitude → invalid")
    void isValid_positiveInfinityLatitude_returnsFalse() {
        assertThat(validator.isValid(new Coords(Double.POSITIVE_INFINITY, 0.0), context)).isFalse();
    }

    @Test
    @DisplayName("isValid: negative Infinity longitude → invalid")
    void isValid_negativeInfinityLongitude_returnsFalse() {
        assertThat(validator.isValid(new Coords(0.0, Double.NEGATIVE_INFINITY), context)).isFalse();
    }
}
