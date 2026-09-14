package com.fiap.hospital.history.projection;

public class InvalidAppointmentEventException extends RuntimeException {

    public InvalidAppointmentEventException(String message) {
        super(message);
    }
}
