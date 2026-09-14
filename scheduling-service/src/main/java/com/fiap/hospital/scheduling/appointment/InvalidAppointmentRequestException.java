package com.fiap.hospital.scheduling.appointment;

public class InvalidAppointmentRequestException extends RuntimeException {

    public InvalidAppointmentRequestException(String message) {
        super(message);
    }
}
