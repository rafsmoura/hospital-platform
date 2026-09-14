package com.fiap.hospital.history.graphql;

import com.fiap.hospital.history.projection.HistoryAppointment;
import com.fiap.hospital.history.projection.HistoryAppointmentRepository;
import com.fiap.hospital.history.security.PatientAccessPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class HistoryQueryService {

    private final HistoryAppointmentRepository appointments;
    private final PatientAccessPolicy accessPolicy;
    private final Clock clock;

    @Autowired
    public HistoryQueryService(HistoryAppointmentRepository appointments, PatientAccessPolicy accessPolicy) {
        this(appointments, accessPolicy, Clock.systemUTC());
    }

    HistoryQueryService(HistoryAppointmentRepository appointments, PatientAccessPolicy accessPolicy, Clock clock) {
        this.appointments = appointments;
        this.accessPolicy = accessPolicy;
        this.clock = clock;
    }

    public List<HistoryAppointment> findHistory(UUID patientId, boolean futureOnly, Instant from,
                                                AppointmentSort sort, Authentication authentication) {
        accessPolicy.assertCanAccessPatient(patientId, authentication);
        if (!futureOnly) {
            return sort == AppointmentSort.DESC
                    ? appointments.findByPatientIdOrderByScheduledAtDesc(patientId)
                    : appointments.findByPatientIdOrderByScheduledAtAsc(patientId);
        }

        Instant effectiveFrom = from == null ? clock.instant() : from;
        return sort == AppointmentSort.DESC
                ? appointments.findByPatientIdAndScheduledAtGreaterThanEqualOrderByScheduledAtDesc(patientId, effectiveFrom)
                : appointments.findByPatientIdAndScheduledAtGreaterThanEqualOrderByScheduledAtAsc(patientId, effectiveFrom);
    }

    public HistoryAppointment findAppointment(UUID appointmentId, Authentication authentication) {
        return appointments.findById(appointmentId)
                .map(appointment -> {
                    accessPolicy.assertCanAccessPatient(appointment.getPatientId(), authentication);
                    return appointment;
                })
                .orElse(null);
    }
}
