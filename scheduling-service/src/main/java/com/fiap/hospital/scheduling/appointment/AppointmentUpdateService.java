package com.fiap.hospital.scheduling.appointment;

import com.fiap.hospital.contract.AppointmentStatus;
import com.fiap.hospital.contract.EventType;
import com.fiap.hospital.scheduling.outbox.AppointmentOutboxService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class AppointmentUpdateService {

    private final AppointmentRepository appointments;
    private final AppointmentRelationshipValidator relationshipValidator;
    private final AppointmentOutboxService outboxService;

    public AppointmentUpdateService(AppointmentRepository appointments,
                                    AppointmentRelationshipValidator relationshipValidator,
                                    AppointmentOutboxService outboxService) {
        this.appointments = appointments;
        this.relationshipValidator = relationshipValidator;
        this.outboxService = outboxService;
    }

    @Transactional
    public AppointmentResponse update(UUID id, UpdateAppointmentRequest request) {
        Appointment appointment = appointments.findById(id)
                .orElseThrow(AppointmentNotFoundException::new);
        if (appointment.getStatus() != AppointmentStatus.AGENDADA) {
            throw new FinalAppointmentException();
        }
        if (appointment.getVersion() != request.version()) {
            throw new AppointmentVersionConflictException();
        }
        AppointmentStatus status = parseStatus(request.status());
        relationshipValidator.validate(request.patientId(), request.doctorId());
        appointment.changeDetails(request.patientId(), request.doctorId(), request.scheduledAt(), status, request.notes());
        try {
            Appointment saved = appointments.saveAndFlush(appointment);
            outboxService.record(saved, EventType.CONSULTA_EDITADA);
            return AppointmentResponse.from(saved);
        } catch (OptimisticLockingFailureException exception) {
            throw new AppointmentVersionConflictException();
        }
    }

    private AppointmentStatus parseStatus(String status) {
        try {
            return AppointmentStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new InvalidAppointmentRequestException("Unsupported appointment status");
        }
    }
}
