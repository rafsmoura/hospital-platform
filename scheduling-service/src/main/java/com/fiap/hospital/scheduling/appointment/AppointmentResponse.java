package com.fiap.hospital.scheduling.appointment;

import com.fiap.hospital.contract.AppointmentStatus;

import java.time.Instant;
import java.util.UUID;

public record AppointmentResponse(
        UUID id,
        UUID patientId,
        UUID doctorId,
        Instant scheduledAt,
        AppointmentStatus status,
        String notes,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(), appointment.getPatientId(), appointment.getDoctorId(),
                appointment.getScheduledAt(), appointment.getStatus(), appointment.getNotes(),
                appointment.getVersion(), appointment.getCreatedAt(), appointment.getUpdatedAt());
    }
}
