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
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.UUID;

@Service
public class AppointmentNotificationConsumer {

    static final String QUEUE = "hospital.notifications.appointments";
    static final String RETRY_HEADER = "x-notification-retry";
    static final int MAX_RETRIES = 3;
    private static final Logger log = LoggerFactory.getLogger(AppointmentNotificationConsumer.class);

    private final MessageProcessingService processingService;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    @Autowired
    public AppointmentNotificationConsumer(MessageProcessingService processingService,
                                           ObjectMapper objectMapper, RabbitTemplate rabbitTemplate) {
        this.processingService = processingService;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = QUEUE, ackMode = "MANUAL")
    public void onMessage(Message message, Channel channel) throws IOException {
        AppointmentEvent event;
        try {
            event = objectMapper.readValue(message.getBody(), AppointmentEvent.class);
            validate(event);
        } catch (JsonProcessingException | InvalidAppointmentEventException exception) {
            processingService.recordFailure(null, exception.getMessage(), new String(message.getBody()));
            channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
            return;
        }

        try {
            processingService.process(event);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            log.info("appointment reminder processed messageId={} appointmentId={} patientId={} eventType={} status={}",
                    event.messageId(), event.appointmentId(), event.patientId(), event.eventType(), event.status());
        } catch (RuntimeException exception) {
            handleTransientFailure(message, channel, event, exception);
        }
    }

    private void handleTransientFailure(Message message, Channel channel, AppointmentEvent event,
                                       RuntimeException exception) throws IOException {
        int retryCount = retryCount(message);
        if (retryCount < MAX_RETRIES) {
            MessageProperties properties = new MessageProperties();
            properties.getHeaders().putAll(message.getMessageProperties().getHeaders());
            properties.setHeader(RETRY_HEADER, retryCount + 1);
            try {
                rabbitTemplate.send("", QUEUE, new Message(message.getBody(), properties));
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            } catch (RuntimeException publishFailure) {
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            }
            return;
        }

        processingService.recordFailure(event.messageId(), exception.getMessage(), null);
        channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
    }

    private int retryCount(Message message) {
        Object value = message.getMessageProperties().getHeaders().get(RETRY_HEADER);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return MAX_RETRIES;
            }
        }
        return 0;
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
