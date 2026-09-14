package com.fiap.hospital.scheduling.appointment;

import com.fiap.hospital.contract.AppointmentStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AppointmentPersistenceIntegrationTest {

    private static final UUID PATIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID DOCTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private AppointmentRepository appointments;

    @Autowired
    private AppointmentRelationshipValidator relationshipValidator;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void persistsAppointmentFieldsAndUtcAuditValues() {
        Instant scheduledAt = Instant.parse("2030-01-10T14:30:00Z");
        Appointment appointment = appointments.saveAndFlush(new Appointment(
                UUID.randomUUID(), PATIENT_ID, DOCTOR_ID, scheduledAt,
                AppointmentStatus.AGENDADA, "Retorno"));

        entityManager.clear();
        Appointment stored = appointments.findById(appointment.getId()).orElseThrow();
        assertThat(stored.getId()).isEqualTo(appointment.getId());
        assertThat(stored.getPatientId()).isEqualTo(PATIENT_ID);
        assertThat(stored.getDoctorId()).isEqualTo(DOCTOR_ID);
        assertThat(stored.getScheduledAt()).isEqualTo(scheduledAt);
        assertThat(stored.getStatus()).isEqualTo(AppointmentStatus.AGENDADA);
        assertThat(stored.getVersion()).isZero();
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.getUpdatedAt()).isEqualTo(stored.getCreatedAt());
    }

    @Test
    void rejectsInvalidPatientAndDoctorRoles() {
        assertThatThrownBy(() -> relationshipValidator.validate(ADMIN_ID, DOCTOR_ID))
                .isInstanceOf(InvalidAppointmentRelationshipException.class)
                .hasMessageContaining("PACIENTE");
        assertThatThrownBy(() -> relationshipValidator.validate(PATIENT_ID, PATIENT_ID))
                .isInstanceOf(InvalidAppointmentRelationshipException.class)
                .hasMessageContaining("MEDICO");
    }

    @Test
    @Transactional
    void rejectsStaleOptimisticLockVersion() {
        Appointment appointment = appointments.saveAndFlush(new Appointment(
                UUID.randomUUID(), PATIENT_ID, DOCTOR_ID, Instant.parse("2030-01-10T14:30:00Z"),
                AppointmentStatus.AGENDADA, "Original"));
        entityManager.clear();

        Appointment first = appointments.findById(appointment.getId()).orElseThrow();
        entityManager.clear();
        Appointment stale = appointments.findById(appointment.getId()).orElseThrow();
        entityManager.clear();
        first.changeDetails(PATIENT_ID, DOCTOR_ID, first.getScheduledAt(), AppointmentStatus.AGENDADA, "Newer");
        appointments.saveAndFlush(first);
        entityManager.clear();

        stale.changeDetails(PATIENT_ID, DOCTOR_ID, stale.getScheduledAt(), AppointmentStatus.AGENDADA, "Stale");
        assertThatThrownBy(() -> appointments.saveAndFlush(stale))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }
}
