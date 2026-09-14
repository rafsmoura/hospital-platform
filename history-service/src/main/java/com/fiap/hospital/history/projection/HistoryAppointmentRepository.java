package com.fiap.hospital.history.projection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface HistoryAppointmentRepository extends JpaRepository<HistoryAppointment, UUID> {

    List<HistoryAppointment> findByPatientIdOrderByScheduledAtAsc(UUID patientId);

    List<HistoryAppointment> findByPatientIdOrderByScheduledAtDesc(UUID patientId);

    List<HistoryAppointment> findByPatientIdAndScheduledAtGreaterThanEqualOrderByScheduledAtAsc(
            UUID patientId, Instant from);

    List<HistoryAppointment> findByPatientIdAndScheduledAtGreaterThanEqualOrderByScheduledAtDesc(
            UUID patientId, Instant from);
}
