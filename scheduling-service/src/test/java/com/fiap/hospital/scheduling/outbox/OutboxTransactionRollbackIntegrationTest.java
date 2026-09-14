package com.fiap.hospital.scheduling.outbox;

import com.fiap.hospital.scheduling.appointment.AppointmentCreationService;
import com.fiap.hospital.scheduling.appointment.AppointmentRelationshipValidator;
import com.fiap.hospital.scheduling.appointment.AppointmentRepository;
import com.fiap.hospital.scheduling.appointment.CreateAppointmentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class OutboxTransactionRollbackIntegrationTest {

    private static final UUID PATIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID DOCTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private AppointmentRepository appointments;

    @Autowired
    private AppointmentCreationService creationService;

    @MockBean
    private OutboxEventRepository outboxEvents;

    @Test
    void outboxFailureRollsBackAppointmentWrite() {
        when(outboxEvents.save(any(OutboxEvent.class)))
                .thenThrow(new IllegalStateException("outbox unavailable"));
        long before = appointments.count();

        assertThatThrownBy(() -> creationService.create(new CreateAppointmentRequest(
                        PATIENT_ID, DOCTOR_ID, Instant.parse("2030-01-10T14:30:00Z"), null, "Rollback")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("outbox unavailable");

        assertThat(appointments.count()).isEqualTo(before);
    }
}
