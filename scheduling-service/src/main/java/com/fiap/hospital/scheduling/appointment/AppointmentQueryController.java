package com.fiap.hospital.scheduling.appointment;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentQueryController {

    private final AppointmentRepository appointments;

    public AppointmentQueryController(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MEDICO', 'ENFERMEIRO')")
    public AppointmentResponse findById(@PathVariable("id") UUID id) {
        return appointments.findById(id)
                .map(AppointmentResponse::from)
                .orElseThrow(AppointmentNotFoundException::new);
    }
}
