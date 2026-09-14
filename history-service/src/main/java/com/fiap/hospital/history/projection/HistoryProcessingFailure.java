package com.fiap.hospital.history.projection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "history_processing_failures")
public class HistoryProcessingFailure {

    @Id
    private UUID id;

    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "failure_reason", nullable = false, length = 1000)
    private String failureReason;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected HistoryProcessingFailure() {
    }

    public HistoryProcessingFailure(UUID messageId, String failureReason, String payload, Instant recordedAt) {
        this.id = UUID.randomUUID();
        this.messageId = messageId;
        this.failureReason = failureReason;
        this.payload = payload;
        this.recordedAt = recordedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
