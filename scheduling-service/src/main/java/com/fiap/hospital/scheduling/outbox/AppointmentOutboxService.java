package com.fiap.hospital.scheduling.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.hospital.contract.AppointmentEvent;
import com.fiap.hospital.contract.EventType;
import com.fiap.hospital.scheduling.appointment.Appointment;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AppointmentOutboxService {

    private final OutboxEventRepository outboxEvents;
    private final ObjectMapper objectMapper;

    public AppointmentOutboxService(OutboxEventRepository outboxEvents, ObjectMapper objectMapper) {
        this.outboxEvents = outboxEvents;
        this.objectMapper = objectMapper;
    }

    public OutboxEvent record(Appointment appointment, EventType eventType) {
        UUID messageId = UUID.randomUUID();
        AppointmentEvent event = new AppointmentEvent(
                1, messageId, eventType, Instant.now(), appointment.getId(), appointment.getPatientId(),
                appointment.getDoctorId(), appointment.getScheduledAt(), appointment.getStatus(), appointment.getNotes());
        try {
            String payload = objectMapper.writeValueAsString(event);
            String routingKey = eventType == EventType.CONSULTA_CRIADA
                    ? "appointment.created" : "appointment.edited";
            return outboxEvents.save(new OutboxEvent(
                    UUID.randomUUID(), messageId, eventType, payload, routingKey, event.occurredAt()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Appointment event serialization failed", exception);
        }
    }
}
