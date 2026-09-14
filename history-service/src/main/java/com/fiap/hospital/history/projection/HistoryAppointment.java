package com.fiap.hospital.history.projection;

import com.fiap.hospital.contract.AppointmentEvent;
import com.fiap.hospital.contract.AppointmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "history_appointments")
public class HistoryAppointment {

    @Id
    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "doctor_id")
    private UUID doctorId;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status;

    @Column(length = 1000)
    private String notes;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "last_event_message_id", nullable = false, unique = true)
    private UUID lastEventMessageId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HistoryAppointment() {
    }

    public HistoryAppointment(AppointmentEvent event, Instant updatedAt) {
        this.appointmentId = event.appointmentId();
        apply(event, updatedAt);
    }

    public void apply(AppointmentEvent event, Instant updatedAt) {
        this.patientId = event.patientId();
        this.doctorId = event.doctorId();
        this.scheduledAt = event.scheduledAt();
        this.status = event.status();
        this.notes = event.notes();
        this.occurredAt = event.occurredAt();
        this.lastEventMessageId = event.messageId();
        this.updatedAt = updatedAt;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getDoctorId() {
        return doctorId;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getLastEventMessageId() {
        return lastEventMessageId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
