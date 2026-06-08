package com.accesspath.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "moderation_actions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModerationAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "action_id", updatable = false, nullable = false)
    private UUID actionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moderator_user_id", nullable = false)
    private User moderator;

    @Column(name = "action_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private ActionType actionType;

    @Column(name = "note")
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum ActionType {
        VERIFY,
        REJECT,
        RESOLVE,
        EXPIRE,
        REQUEST_CLARIFICATION
    }
}