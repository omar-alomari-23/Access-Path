package com.accesspath.repositories;

import com.accesspath.models.Vote;
import com.accesspath.models.Vote.VoteType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VoteRepository extends JpaRepository<Vote, UUID> {

    // Check if user already voted on a report
    boolean existsByReport_ReportIdAndUser_UserId(
        UUID reportId,
        UUID userId
    );

    // Get existing vote to prevent duplicates
    Optional<Vote> findByReport_ReportIdAndUser_UserId(
        UUID reportId,
        UUID userId
    );

    // Count confirmations for confidence scoring
    long countByReport_ReportIdAndVoteType(
        UUID reportId,
        VoteType voteType
    );

    // Get all votes for a report
    java.util.List<Vote> findByReport_ReportId(UUID reportId);
}