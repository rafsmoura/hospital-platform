package com.fiap.hospital.scheduling.appointment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateAppointmentRequest(
        @NotNull UUID patientId,
        UUID doctorId,
        @NotNull Instant scheduledAt,
        String status,
        @Size(max = 1000) String notes) {
}
