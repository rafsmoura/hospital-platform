package com.fiap.hospital.history.graphql;

import com.fiap.hospital.contract.AppointmentStatus;
import com.fiap.hospital.history.projection.HistoryAppointment;

import java.time.Instant;

public record AppointmentView(
        String id,
        String patientId,
        String doctorId,
        Instant scheduledAt,
        AppointmentStatus status,
        String notes) {

    public static AppointmentView from(HistoryAppointment appointment) {
        return new AppointmentView(
                appointment.getAppointmentId().toString(),
                appointment.getPatientId().toString(),
                appointment.getDoctorId() == null ? null : appointment.getDoctorId().toString(),
                appointment.getScheduledAt(),
                appointment.getStatus(),
                appointment.getNotes());
    }
}
