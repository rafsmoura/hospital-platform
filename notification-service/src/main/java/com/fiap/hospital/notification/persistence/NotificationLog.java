package com.fiap.hospital.notification.persistence;

import com.fiap.hospital.contract.EventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_logs")
public class NotificationLog {

    @Id
    private UUID id;

    @Column(name = "message_id", nullable = false, unique = true)
    private UUID messageId;

    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EventType eventType;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationStatus status;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected NotificationLog() {
    }

    public NotificationLog(UUID messageId, UUID appointmentId, UUID patientId, EventType eventType,
                           Instant scheduledAt, NotificationStatus status, String failureReason,
                           Instant processedAt) {
        this.id = UUID.randomUUID();
        this.messageId = messageId;
        this.appointmentId = appointmentId;
        this.patientId = patientId;
        this.eventType = eventType;
        this.scheduledAt = scheduledAt;
        this.status = status;
        this.failureReason = failureReason;
        this.processedAt = processedAt;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public EventType getEventType() {
        return eventType;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
