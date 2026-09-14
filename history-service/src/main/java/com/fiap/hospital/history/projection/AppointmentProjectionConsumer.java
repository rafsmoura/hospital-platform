package com.fiap.hospital.history.projection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.hospital.contract.AppointmentEvent;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

@Service
public class AppointmentProjectionConsumer {

    private final HistoryAppointmentRepository appointments;
    private final HistoryProcessedMessageRepository processedMessages;
    private final HistoryProcessingFailureRepository failures;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public AppointmentProjectionConsumer(HistoryAppointmentRepository appointments,
                                         HistoryProcessedMessageRepository processedMessages,
                                         HistoryProcessingFailureRepository failures,
                                         ObjectMapper objectMapper) {
        this(appointments, processedMessages, failures, objectMapper, Clock.systemUTC());
    }

    AppointmentProjectionConsumer(HistoryAppointmentRepository appointments,
                                   HistoryProcessedMessageRepository processedMessages,
                                   HistoryProcessingFailureRepository failures,
                                   ObjectMapper objectMapper, Clock clock) {
        this.appointments = appointments;
        this.processedMessages = processedMessages;
        this.failures = failures;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @RabbitListener(queues = "hospital.history.appointments", ackMode = "MANUAL")
    public void onMessage(Message message, Channel channel) throws IOException {
        AppointmentEvent event;
        try {
            event = objectMapper.readValue(message.getBody(), AppointmentEvent.class);
            validate(event);
        } catch (JsonProcessingException | InvalidAppointmentEventException exception) {
            recordFailure(message, exception.getMessage());
            channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
            return;
        }

        project(event);
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    @Transactional
    void project(AppointmentEvent event) {
        if (processedMessages.existsById(event.messageId())) {
            return;
        }

        HistoryAppointment projection = appointments.findById(event.appointmentId()).orElse(null);
        if (projection == null) {
            appointments.save(new HistoryAppointment(event, clock.instant()));
        } else if (event.occurredAt().isAfter(projection.getOccurredAt())) {
            projection.apply(event, clock.instant());
            appointments.save(projection);
        }
        processedMessages.save(new HistoryProcessedMessage(event.messageId(), clock.instant()));
    }

    private void validate(AppointmentEvent event) {
        if (event == null || event.eventVersion() != 1) {
            throw new InvalidAppointmentEventException("eventVersion must be 1");
        }
        if (event.messageId() == null) {
            throw new InvalidAppointmentEventException("messageId is required");
        }
        if (event.eventType() == null) {
            throw new InvalidAppointmentEventException("eventType is required");
        }
        if (event.occurredAt() == null) {
            throw new InvalidAppointmentEventException("occurredAt is required");
        }
        if (event.appointmentId() == null) {
            throw new InvalidAppointmentEventException("appointmentId is required");
        }
        if (event.patientId() == null) {
            throw new InvalidAppointmentEventException("patientId is required");
        }
        if (event.scheduledAt() == null) {
            throw new InvalidAppointmentEventException("scheduledAt is required");
        }
        if (event.status() == null) {
            throw new InvalidAppointmentEventException("status is required");
        }
    }

    private void recordFailure(Message message, String reason) {
        Instant now = clock.instant();
        failures.save(new HistoryProcessingFailure(null, reason, new String(message.getBody()), now));
    }
}
