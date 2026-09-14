package com.fiap.hospital.scheduling.appointment;

public class FinalAppointmentException extends RuntimeException {

    public FinalAppointmentException() {
        super("Final-state appointments cannot be edited");
    }
}
