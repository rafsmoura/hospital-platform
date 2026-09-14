package com.fiap.hospital.scheduling.appointment;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentCreationController {

    private final AppointmentCreationService creationService;

    public AppointmentCreationController(AppointmentCreationService creationService) {
        this.creationService = creationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ENFERMEIRO')")
    public AppointmentResponse create(@Valid @RequestBody CreateAppointmentRequest request) {
        return creationService.create(request);
    }
}
