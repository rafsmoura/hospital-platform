package com.fiap.hospital.scheduling.appointment;

import com.fiap.hospital.contract.AppointmentStatus;
import com.fiap.hospital.contract.EventType;
import com.fiap.hospital.scheduling.outbox.AppointmentOutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AppointmentCreationService {

    private final AppointmentRepository appointments;
    private final AppointmentRelationshipValidator relationshipValidator;
    private final AppointmentOutboxService outboxService;

    public AppointmentCreationService(AppointmentRepository appointments,
                                      AppointmentRelationshipValidator relationshipValidator,
                                      AppointmentOutboxService outboxService) {
        this.appointments = appointments;
        this.relationshipValidator = relationshipValidator;
        this.outboxService = outboxService;
    }

    @Transactional
    public AppointmentResponse create(CreateAppointmentRequest request) {
        if (!request.scheduledAt().isAfter(Instant.now())) {
            throw new InvalidAppointmentRequestException("scheduledAt must be in the future");
        }
        if (request.status() != null && parseStatus(request.status()) != AppointmentStatus.AGENDADA) {
            throw new InvalidAppointmentRequestException("New appointments must have AGENDADA status");
        }
        relationshipValidator.validate(request.patientId(), request.doctorId());

        Appointment appointment = new Appointment(
                UUID.randomUUID(), request.patientId(), request.doctorId(), request.scheduledAt(),
                AppointmentStatus.AGENDADA, request.notes());
        Appointment saved = appointments.save(appointment);
        outboxService.record(saved, EventType.CONSULTA_CRIADA);
        return AppointmentResponse.from(saved);
    }

    private AppointmentStatus parseStatus(String status) {
        try {
            return AppointmentStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new InvalidAppointmentRequestException("Unsupported appointment status");
        }
    }
}
