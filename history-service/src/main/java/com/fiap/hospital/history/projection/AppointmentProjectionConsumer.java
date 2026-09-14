package com.fiap.hospital.history.projection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.hospital.contract.AppointmentEvent;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class AppointmentProjectionConsumer {

    private final HistoryProjectionService projectionService;
    private final HistoryProcessingFailureRepository failures;
    private final ObjectMapper objectMapper;
    private final RetryTemplate retryTemplate;
    private final Clock clock;

    @Autowired
    public AppointmentProjectionConsumer(HistoryProjectionService projectionService,
                                         HistoryProcessingFailureRepository failures,
                                         ObjectMapper objectMapper, RetryTemplate retryTemplate) {
        this(projectionService, failures, objectMapper, retryTemplate, Clock.systemUTC());
    }

    AppointmentProjectionConsumer(HistoryProjectionService projectionService,
                                   HistoryProcessingFailureRepository failures,
                                   ObjectMapper objectMapper, RetryTemplate retryTemplate, Clock clock) {
        this.projectionService = projectionService;
        this.failures = failures;
        this.objectMapper = objectMapper;
        this.retryTemplate = retryTemplate;
        this.clock = clock;
    }

    @RabbitListener(queues = "hospital.history.appointments", ackMode = "MANUAL")
    public void onMessage(Message message, Channel channel) throws IOException {
        AppointmentEvent event;
        try {
            event = objectMapper.readValue(message.getBody(), AppointmentEvent.class);
            validate(event);
        } catch (JsonProcessingException | InvalidAppointmentEventException exception) {
            recordFailure(null, exception.getMessage(), message);
            channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
            return;
        }

        try {
            retryTemplate.execute(context -> {
                projectionService.project(event);
                return null;
            });
        } catch (RuntimeException exception) {
            recordFailure(event.messageId(), exception.getMessage(), message);
            channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
            return;
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
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

    private void recordFailure(UUID messageId, String reason, Message message) {
        Instant now = clock.instant();
        failures.save(new HistoryProcessingFailure(messageId, reason,
                new String(message.getBody(), StandardCharsets.UTF_8), now));
    }
}
