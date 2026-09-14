package com.fiap.hospital.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.hospital.contract.AppointmentEvent;
import com.fiap.hospital.contract.AppointmentStatus;
import com.fiap.hospital.contract.EventType;
import com.fiap.hospital.notification.persistence.NotificationLog;
import com.fiap.hospital.notification.persistence.NotificationLogRepository;
import com.fiap.hospital.notification.persistence.NotificationProcessedMessageRepository;
import com.fiap.hospital.notification.persistence.NotificationProcessingFailureRepository;
import com.fiap.hospital.notification.persistence.NotificationStatus;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class AppointmentNotificationConsumerIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");
    private static final UUID PATIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID DOCTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID APPOINTMENT_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @Autowired
    private AppointmentNotificationConsumer consumer;

    @Autowired
    private NotificationLogRepository notificationLogs;

    @Autowired
    private NotificationProcessingFailureRepository failures;

    @Autowired
    private NotificationProcessedMessageRepository processedMessages;

    @SpyBean
    private NotificationLogRepository notificationLogStore;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanLogs() {
        notificationLogs.deleteAll();
        failures.deleteAll();
        processedMessages.deleteAll();
    }

    @Test
    void futureCreatedAndEditedEventsPersistCompleteSentReminders() throws Exception {
        AppointmentEvent created = event(UUID.fromString("55555555-5555-5555-5555-555555555555"),
                EventType.CONSULTA_CRIADA, AppointmentStatus.AGENDADA,
                Instant.parse("2026-09-20T14:00:00Z"), "created note");
        AppointmentEvent edited = event(UUID.fromString("66666666-6666-6666-6666-666666666666"),
                EventType.CONSULTA_EDITADA, AppointmentStatus.AGENDADA,
                Instant.parse("2026-09-21T14:00:00Z"), "edited note");
        Channel channel = mock(Channel.class);

        consumer.onMessage(message(created, 41L), channel);
        consumer.onMessage(message(edited, 42L), channel);

        NotificationLog createdLog = notificationLogs.findByMessageId(created.messageId()).orElseThrow();
        NotificationLog editedLog = notificationLogs.findByMessageId(edited.messageId()).orElseThrow();
        assertThat(notificationLogs.count()).isEqualTo(2);
        assertThat(createdLog.getMessageId()).isEqualTo(created.messageId());
        assertThat(createdLog.getAppointmentId()).isEqualTo(APPOINTMENT_ID);
        assertThat(createdLog.getPatientId()).isEqualTo(PATIENT_ID);
        assertThat(createdLog.getEventType()).isEqualTo(EventType.CONSULTA_CRIADA);
        assertThat(createdLog.getScheduledAt()).isEqualTo(Instant.parse("2026-09-20T14:00:00Z"));
        assertThat(createdLog.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(createdLog.getProcessedAt()).isNotNull();
        assertThat(editedLog.getEventType()).isEqualTo(EventType.CONSULTA_EDITADA);
        assertThat(editedLog.getStatus()).isEqualTo(NotificationStatus.SENT);
        verify(channel).basicAck(41L, false);
        verify(channel).basicAck(42L, false);
    }

    @Test
    void cancelledFutureEventIsRecordedAsSkippedWithoutSentReminder(CapturedOutput output) throws Exception {
        AppointmentEvent cancelled = event(UUID.fromString("77777777-7777-7777-7777-777777777777"),
                EventType.CONSULTA_EDITADA, AppointmentStatus.CANCELADA,
                Instant.parse("2026-09-22T14:00:00Z"), "confidential clinical note");
        Channel channel = mock(Channel.class);

        consumer.onMessage(message(cancelled, 51L), channel);

        NotificationLog log = notificationLogs.findByMessageId(cancelled.messageId()).orElseThrow();
        assertThat(log.getStatus()).isEqualTo(NotificationStatus.SKIPPED_CANCELLED);
        assertThat(log.getAppointmentId()).isEqualTo(APPOINTMENT_ID);
        assertThat(log.getPatientId()).isEqualTo(PATIENT_ID);
        assertThat(log.getEventType()).isEqualTo(EventType.CONSULTA_EDITADA);
        assertThat(log.getFailureReason()).isNull();
        assertThat(notificationLogs.findAll()).noneMatch(item -> item.getStatus() == NotificationStatus.SENT);
        assertThat(output.getOut()).contains(cancelled.messageId().toString(), APPOINTMENT_ID.toString());
        assertThat(output.getOut()).doesNotContain("confidential clinical note", "password");
        verify(channel).basicAck(51L, false);
    }

    @Test
    void duplicateMessageCreatesAtMostOneSuccessfulReminder() throws Exception {
        AppointmentEvent created = event(UUID.fromString("88888888-8888-8888-8888-888888888888"),
                EventType.CONSULTA_CRIADA, AppointmentStatus.AGENDADA,
                Instant.parse("2026-09-23T14:00:00Z"), "duplicate test");
        Channel channel = mock(Channel.class);

        consumer.onMessage(message(created, 61L), channel);
        consumer.onMessage(message(created, 62L), channel);

        assertThat(notificationLogs.count()).isEqualTo(1);
        assertThat(notificationLogs.findByMessageId(created.messageId())).isPresent();
        assertThat(failures.count()).isZero();
        verify(channel).basicAck(61L, false);
        verify(channel).basicAck(62L, false);
    }

    @Test
    void invalidEventIsRecordedAsFailureAndRejectedWithoutReminder() throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(71L);
        Message invalid = new Message("{\"eventVersion\":2,\"messageId\":null}".getBytes(), properties);
        Channel channel = mock(Channel.class);

        consumer.onMessage(invalid, channel);

        assertThat(notificationLogs.count()).isZero();
        assertThat(failures.count()).isEqualTo(1);
        assertThat(failures.findAll().get(0).getFailureReason()).contains("eventVersion");
        verify(channel).basicReject(71L, false);
    }

    @Test
    void transientFailuresAreRetriedThreeTimesThenRejectedToDlq() throws Exception {
        AppointmentEvent created = event(UUID.fromString("99999999-9999-9999-9999-999999999999"),
                EventType.CONSULTA_CRIADA, AppointmentStatus.AGENDADA,
                Instant.parse("2026-09-24T14:00:00Z"), "retry test");
        Channel channel = mock(Channel.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(notificationLogStore).save(any(NotificationLog.class));

        consumer.onMessage(message(created, 81L), channel);

        assertThat(notificationLogs.count()).isZero();
        assertThat(failures.count()).isEqualTo(1);
        assertThat(failures.findAll().get(0).getFailureReason()).contains("database unavailable");
        verify(notificationLogStore, times(4)).save(any(NotificationLog.class));
        verify(channel).basicReject(81L, false);
    }

    private Message message(AppointmentEvent event, long deliveryTag) throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(deliveryTag);
        return new Message(objectMapper.writeValueAsBytes(event), properties);
    }

    private AppointmentEvent event(UUID messageId, EventType eventType, AppointmentStatus status,
                                   Instant scheduledAt, String notes) {
        return new AppointmentEvent(1, messageId, eventType, NOW, APPOINTMENT_ID,
                PATIENT_ID, DOCTOR_ID, scheduledAt, status, notes);
    }
}
