package com.accesspath.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "report_id", updatable = false, nullable = false)
    private UUID reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_user_id", nullable = false)
    private User reporter;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "last_updated_at")
    private Instant lastUpdatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private ReportStatus status;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "location_point", columnDefinition = "geometry(Point,4326)")
    private Point locationPoint;

    @Column(name = "confidence_score")
    private Float confidenceScore;

    public enum ReportStatus {
        PENDING,
        VERIFIED,
        REJECTED,
        RESOLVED,
        EXPIRED
    }
}