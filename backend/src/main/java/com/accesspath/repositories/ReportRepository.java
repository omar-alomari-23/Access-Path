package com.accesspath.repositories;

import com.accesspath.models.Report;
import com.accesspath.models.Report.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {

    // Get all reports by status (moderation queue)
    List<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status);

    // Get reports by reporter
    List<Report> findByReporter_UserId(UUID userId);

    // Find nearby reports using PostGIS
    @Query(value = """
        SELECT * FROM reports
        WHERE ST_DWithin(
            CAST(location_point AS geography),
            CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography),
            :radiusMeters
        )
        AND status NOT IN ('REJECTED', 'EXPIRED')
        ORDER BY created_at DESC
        """, nativeQuery = true)
    List<Report> findNearby(
        @Param("lat") double lat,
        @Param("lng") double lng,
        @Param("radiusMeters") double radiusMeters
    );

    // Find reports near route segments for confidence cues
    @Query(value = """
        SELECT * FROM reports
        WHERE ST_DWithin(
            CAST(location_point AS geography),
            CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography),
            :radiusMeters
        )
        AND status = 'VERIFIED'
        ORDER BY created_at DESC
        """, nativeQuery = true)
    List<Report> findVerifiedNearby(
        @Param("lat") double lat,
        @Param("lng") double lng,
        @Param("radiusMeters") double radiusMeters
    );

    // Find expired reports for TTL scheduler
    List<Report> findByExpiresAtBeforeAndStatusNot(
        Instant now,
        ReportStatus status
    );
}