package com.fiap.hospital.scheduling.appointment;

public class AppointmentVersionConflictException extends RuntimeException {

    public AppointmentVersionConflictException() {
        super("Appointment version conflict");
    }
}
