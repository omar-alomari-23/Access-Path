package com.accesspath.repositories;

import com.accesspath.models.ModerationAction;
import com.accesspath.models.ModerationAction.ActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ModerationActionRepository 
    extends JpaRepository<ModerationAction, UUID> {

    // Get all actions for a report (audit trail)
    List<ModerationAction> findByReport_ReportIdOrderByCreatedAtDesc(
        UUID reportId
    );

    // Get all actions by a moderator
    List<ModerationAction> findByModerator_UserId(UUID moderatorId);

    // Check if action type already exists for a report
    boolean existsByReport_ReportIdAndActionType(
        UUID reportId,
        ActionType actionType
    );
}