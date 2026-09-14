package com.fiap.hospital.notification.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.hospital.contract.AppointmentEvent;
import com.fiap.hospital.contract.EventType;
import com.fiap.hospital.notification.processing.MessageProcessingService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class AppointmentNotificationConsumer {

    static final String QUEUE = "hospital.notifications.appointments";
    private static final Logger log = LoggerFactory.getLogger(AppointmentNotificationConsumer.class);

    private final MessageProcessingService processingService;
    private final ObjectMapper objectMapper;
    private final RetryTemplate retryTemplate;

    public AppointmentNotificationConsumer(MessageProcessingService processingService,
                                           ObjectMapper objectMapper, RetryTemplate retryTemplate) {
        this.processingService = processingService;
        this.objectMapper = objectMapper;
        this.retryTemplate = retryTemplate;
    }

    @RabbitListener(queues = QUEUE, ackMode = "MANUAL")
    public void onMessage(Message message, Channel channel) throws IOException {
        AppointmentEvent event;
        try {
            event = objectMapper.readValue(message.getBody(), AppointmentEvent.class);
            validate(event);
        } catch (JsonProcessingException | InvalidAppointmentEventException exception) {
            processingService.recordFailure(null, exception.getMessage(),
                    new String(message.getBody(), StandardCharsets.UTF_8));
            channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
            return;
        }

        try {
            retryTemplate.execute(context -> {
                processingService.process(event);
                return null;
            });
        } catch (RuntimeException exception) {
            processingService.recordFailure(event.messageId(), exception.getMessage(), null);
            channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
            return;
        }

        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        log.info("appointment reminder processed messageId={} appointmentId={} patientId={} eventType={} status={}",
                event.messageId(), event.appointmentId(), event.patientId(), event.eventType(), event.status());
    }

    private void validate(AppointmentEvent event) {
        if (event == null || event.eventVersion() != 1) {
            throw new InvalidAppointmentEventException("eventVersion must be 1");
        }
        if (event.messageId() == null) {
            throw new InvalidAppointmentEventException("messageId is required");
        }
        if (event.eventType() != EventType.CONSULTA_CRIADA && event.eventType() != EventType.CONSULTA_EDITADA) {
            throw new InvalidAppointmentEventException("eventType is invalid");
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

    private static final class InvalidAppointmentEventException extends RuntimeException {
        private InvalidAppointmentEventException(String message) {
            super(message);
        }
    }
}
