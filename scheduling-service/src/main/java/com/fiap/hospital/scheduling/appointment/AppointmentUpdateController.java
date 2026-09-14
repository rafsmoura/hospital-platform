package com.fiap.hospital.scheduling.appointment;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentUpdateController {

    private final AppointmentUpdateService updateService;

    public AppointmentUpdateController(AppointmentUpdateService updateService) {
        this.updateService = updateService;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MEDICO', 'ENFERMEIRO')")
    public AppointmentResponse update(@PathVariable("id") UUID id,
                                      @Valid @RequestBody UpdateAppointmentRequest request) {
        return updateService.update(id, request);
    }
}
