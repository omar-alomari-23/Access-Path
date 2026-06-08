package com.accesspath.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ClassificationService {

    // Keyword maps for category suggestion
    private static final Map<String, Set<String>> CATEGORY_KEYWORDS = Map.of(
        "STEP_FREE_ISSUE", Set.of(
            "stairs", "steps", "ramp", "slope", "lift", "elevator",
            "escalator", "incline", "steep", "wheelchair"
        ),
        "SURFACE_HAZARD", Set.of(
            "pothole", "crack", "uneven", "broken", "damaged",
            "pavement", "slippery", "wet", "ice", "gravel"
        ),
        "OBSTRUCTION", Set.of(
            "blocked", "construction", "scaffolding", "barrier",
            "closed", "roadworks", "obstacle", "gate", "locked"
        ),
        "LIGHTING_ISSUE", Set.of(
            "dark", "light", "lighting", "unlit", "poorly lit",
            "streetlight", "lamp", "visibility"
        ),
        "HEAT_NO_SHADE", Set.of(
            "hot", "heat", "shade", "sun", "exposed",
            "no shelter", "bench", "rest"
        )
    );

    // Keyword maps for severity suggestion
    private static final Map<String, Set<String>> SEVERITY_KEYWORDS = Map.of(
        "HIGH", Set.of(
            "dangerous", "unsafe", "urgent", "emergency", "blocked",
            "impassable", "broken", "collapsed", "flooded"
        ),
        "MEDIUM", Set.of(
            "difficult", "hard", "problematic", "challenging",
            "damaged", "poor", "bad"
        ),
        "LOW", Set.of(
            "minor", "small", "slight", "little",
            "inconvenient", "annoying"
        )
    );

    private static boolean containsWord(String text, String keyword) {
        return Pattern.compile("\\b" + Pattern.quote(keyword) + "\\b")
                .matcher(text)
                .find();
    }

    public String suggestCategory(String description) {
        if (description == null || description.isBlank()) {
            return "OBSTRUCTION";
        }

        String lower = description.toLowerCase();
        String bestCategory = "OBSTRUCTION";
        int bestScore = 0;

        for (Map.Entry<String, Set<String>> entry :
                CATEGORY_KEYWORDS.entrySet()) {
            int score = 0;
            for (String keyword : entry.getValue()) {
                if (containsWord(lower, keyword)) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestCategory = entry.getKey();
            }
        }

        log.debug("Category suggested: {} (score: {})",
            bestCategory, bestScore);
        return bestCategory;
    }

    public String suggestSeverity(String description) {
        if (description == null || description.isBlank()) {
            return "MEDIUM";
        }

        String lower = description.toLowerCase();
        String bestSeverity = "MEDIUM";
        int bestScore = 0;

        for (Map.Entry<String, Set<String>> entry :
                SEVERITY_KEYWORDS.entrySet()) {
            int score = 0;
            for (String keyword : entry.getValue()) {
                if (containsWord(lower, keyword)) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestSeverity = entry.getKey();
            }
        }

        log.debug("Severity suggested: {} (score: {})",
            bestSeverity, bestScore);
        return bestSeverity;
    }

    public boolean getConfidenceFlag(String description) {
        if (description == null || description.isBlank()) {
            return false;
        }

        String lower = description.toLowerCase();
        int totalMatches = 0;

        for (Set<String> keywords : CATEGORY_KEYWORDS.values()) {
            for (String keyword : keywords) {
                if (containsWord(lower, keyword)) totalMatches++;
            }
        }

        // Confident if 2+ keywords matched
        return totalMatches >= 2;
    }
}