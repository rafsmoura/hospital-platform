package com.fiap.hospital.history.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.hospital.contract.AppointmentEvent;
import com.fiap.hospital.contract.AppointmentStatus;
import com.fiap.hospital.contract.EventType;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@SpringBootTest
class AppointmentProjectionConsumerIntegrationTest {

    private static final UUID PATIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID DOCTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID APPOINTMENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private AppointmentProjectionConsumer consumer;

    @SpyBean
    private HistoryAppointmentRepository appointments;

    @Autowired
    private HistoryProcessingFailureRepository failures;

    @Autowired
    private HistoryProcessedMessageRepository processedMessages;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanProjectionState() {
        processedMessages.deleteAll();
        failures.deleteAll();
        appointments.deleteAll();
    }

    @Test
    void createdEventIsProjectedWithItsAppointmentFieldsAndManualAck() throws Exception {
        AppointmentEvent event = event(UUID.fromString("11111111-1111-1111-1111-111111111111"),
                EventType.CONSULTA_CRIADA, Instant.parse("2026-09-14T10:00:00Z"),
                AppointmentStatus.AGENDADA, Instant.parse("2026-09-20T14:00:00Z"), "Retorno");
        Channel channel = mock(Channel.class);

        consumer.onMessage(message(event, 11L), channel);

        HistoryAppointment projection = appointments.findById(APPOINTMENT_ID).orElseThrow();
        assertThat(projection.getAppointmentId()).isEqualTo(APPOINTMENT_ID);
        assertThat(projection.getPatientId()).isEqualTo(PATIENT_ID);
        assertThat(projection.getDoctorId()).isEqualTo(DOCTOR_ID);
        assertThat(projection.getScheduledAt()).isEqualTo(Instant.parse("2026-09-20T14:00:00Z"));
        assertThat(projection.getStatus()).isEqualTo(AppointmentStatus.AGENDADA);
        assertThat(projection.getNotes()).isEqualTo("Retorno");
        assertThat(projection.getLastEventMessageId()).isEqualTo(event.messageId());
        assertThat(processedMessages.count()).isEqualTo(1);
        verify(channel).basicAck(11L, false);
    }

    @Test
    void duplicateAndStaleEventsDoNotRegressTheLatestProjection() throws Exception {
        AppointmentEvent created = event(UUID.fromString("22222222-2222-2222-2222-222222222222"),
                EventType.CONSULTA_CRIADA, Instant.parse("2026-09-14T10:00:00Z"),
                AppointmentStatus.AGENDADA, Instant.parse("2026-09-20T14:00:00Z"), "Inicial");
        AppointmentEvent edited = event(UUID.fromString("33333333-3333-3333-3333-333333333333"),
                EventType.CONSULTA_EDITADA, Instant.parse("2026-09-14T12:00:00Z"),
                AppointmentStatus.CANCELADA, Instant.parse("2026-09-21T14:00:00Z"), "Cancelada");
        AppointmentEvent stale = event(UUID.fromString("44444444-4444-4444-4444-444444444444"),
                EventType.CONSULTA_EDITADA, Instant.parse("2026-09-14T11:00:00Z"),
                AppointmentStatus.REALIZADA, Instant.parse("2026-09-19T14:00:00Z"), "Stale");
        Channel channel = mock(Channel.class);

        consumer.onMessage(message(created, 21L), channel);
        consumer.onMessage(message(edited, 22L), channel);
        consumer.onMessage(message(edited, 23L), channel);
        consumer.onMessage(message(stale, 24L), channel);

        HistoryAppointment projection = appointments.findById(APPOINTMENT_ID).orElseThrow();
        assertThat(appointments.count()).isEqualTo(1);
        assertThat(projection.getStatus()).isEqualTo(AppointmentStatus.CANCELADA);
        assertThat(projection.getScheduledAt()).isEqualTo(Instant.parse("2026-09-21T14:00:00Z"));
        assertThat(projection.getNotes()).isEqualTo("Cancelada");
        assertThat(projection.getLastEventMessageId()).isEqualTo(edited.messageId());
        assertThat(processedMessages.count()).isEqualTo(3);
        verify(channel, times(4)).basicAck(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.eq(false));
    }

    @Test
    void invalidEventIsRecordedAndRejectedWithoutCreatingAProjection() throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(31L);
        Message invalid = new Message("{\"eventVersion\":2,\"messageId\":null}".getBytes(), properties);
        Channel channel = mock(Channel.class);

        consumer.onMessage(invalid, channel);

        assertThat(appointments.count()).isZero();
        assertThat(processedMessages.count()).isZero();
        assertThat(failures.count()).isEqualTo(1);
        assertThat(failures.findAll().get(0).getFailureReason()).contains("eventVersion");
        verify(channel).basicReject(31L, false);
    }

    @Test
    void transientProjectionFailureRetriesThreeTimesBeforeRejecting() throws Exception {
        AppointmentEvent event = event(UUID.fromString("55555555-5555-5555-5555-555555555555"),
                EventType.CONSULTA_CRIADA, Instant.parse("2026-09-14T10:00:00Z"),
                AppointmentStatus.AGENDADA, Instant.parse("2026-09-20T14:00:00Z"), "Retry");
        doThrow(new IllegalStateException("database unavailable"))
                .when(appointments).save(any(HistoryAppointment.class));
        Channel channel = mock(Channel.class);

        consumer.onMessage(message(event, 32L), channel);

        assertThat(appointments.count()).isZero();
        assertThat(processedMessages.count()).isZero();
        assertThat(failures.count()).isEqualTo(1);
        assertThat(failures.findAll().get(0).getFailureReason()).contains("database unavailable");
        verify(appointments, times(4)).save(any(HistoryAppointment.class));
        verify(channel).basicReject(32L, false);
    }

    private Message message(AppointmentEvent event, long deliveryTag) throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(deliveryTag);
        return new Message(objectMapper.writeValueAsBytes(event), properties);
    }

    private AppointmentEvent event(UUID messageId, EventType eventType, Instant occurredAt,
                                   AppointmentStatus status, Instant scheduledAt, String notes) {
        return new AppointmentEvent(1, messageId, eventType, occurredAt, APPOINTMENT_ID,
                PATIENT_ID, DOCTOR_ID, scheduledAt, status, notes);
    }
}
