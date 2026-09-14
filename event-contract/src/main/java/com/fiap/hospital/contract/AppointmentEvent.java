package com.fiap.hospital.contract;

import java.time.Instant;
import java.util.UUID;

public record AppointmentEvent(
        int eventVersion,
        UUID messageId,
        EventType eventType,
        Instant occurredAt,
        UUID appointmentId,
        UUID patientId,
        UUID doctorId,
        Instant scheduledAt,
        AppointmentStatus status,
        String notes) {
}
