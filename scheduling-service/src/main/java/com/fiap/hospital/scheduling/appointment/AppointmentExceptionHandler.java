package com.fiap.hospital.scheduling.appointment;

import org.springframework.http.ResponseEntity;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class AppointmentExceptionHandler {

    @ExceptionHandler({InvalidAppointmentRequestException.class, InvalidAppointmentRelationshipException.class})
    ResponseEntity<Map<String, String>> invalidAppointment(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(AppointmentNotFoundException.class)
    ResponseEntity<Map<String, String>> appointmentNotFound(AppointmentNotFoundException exception) {
        return ResponseEntity.status(404).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler({FinalAppointmentException.class, AppointmentVersionConflictException.class,
            OptimisticLockingFailureException.class})
    ResponseEntity<Map<String, String>> appointmentConflict(RuntimeException exception) {
        return ResponseEntity.status(409).body(Map.of("error", exception.getMessage()));
    }
}
