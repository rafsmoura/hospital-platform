package com.fiap.hospital.notification.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_processing_failures")
public class NotificationProcessingFailure {

    @Id
    private UUID id;

    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "failure_reason", nullable = false, length = 1000)
    private String failureReason;

    @Column(name = "payload", length = 4000)
    private String payload;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected NotificationProcessingFailure() {
    }

    public NotificationProcessingFailure(UUID messageId, String failureReason, String payload, Instant recordedAt) {
        this.id = UUID.randomUUID();
        this.messageId = messageId;
        this.failureReason = failureReason;
        this.payload = payload;
        this.recordedAt = recordedAt;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
