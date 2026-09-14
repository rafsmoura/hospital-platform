package com.fiap.hospital.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppointmentEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void serializesDocumentedFieldsAndValues() throws Exception {
        UUID messageId = UUID.fromString("2c5c5f9e-f5a6-45e3-bb88-d2ea9efc1b5e");
        UUID appointmentId = UUID.fromString("6c3d9f87-62f4-4bcb-878b-9f5b4d75b43c");
        UUID patientId = UUID.fromString("4e4f6e1e-f3c8-4fd6-82c4-0fc7937d1e2f");
        UUID doctorId = UUID.fromString("b6a1db6d-b3da-4fb5-8f1b-6f2d6a5b0c19");
        AppointmentEvent event = new AppointmentEvent(
                1,
                messageId,
                EventType.CONSULTA_CRIADA,
                Instant.parse("2026-09-13T12:00:00Z"),
                appointmentId,
                patientId,
                doctorId,
                Instant.parse("2026-09-20T14:00:00Z"),
                AppointmentStatus.AGENDADA,
                "Retorno");

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(event));

        assertEquals(1, json.path("eventVersion").asInt());
        assertEquals(messageId.toString(), json.path("messageId").asText());
        assertEquals("CONSULTA_CRIADA", json.path("eventType").asText());
        assertEquals("2026-09-13T12:00:00Z", json.path("occurredAt").asText());
        assertEquals(appointmentId.toString(), json.path("appointmentId").asText());
        assertEquals(patientId.toString(), json.path("patientId").asText());
        assertEquals(doctorId.toString(), json.path("doctorId").asText());
        assertEquals("2026-09-20T14:00:00Z", json.path("scheduledAt").asText());
        assertEquals("AGENDADA", json.path("status").asText());
        assertEquals("Retorno", json.path("notes").asText());
    }

    @Test
    void serializesAllEventTypesAndAppointmentStatusesByName() throws Exception {
        assertEquals("\"CONSULTA_CRIADA\"", objectMapper.writeValueAsString(EventType.CONSULTA_CRIADA));
        assertEquals("\"CONSULTA_EDITADA\"", objectMapper.writeValueAsString(EventType.CONSULTA_EDITADA));
        assertEquals("\"AGENDADA\"", objectMapper.writeValueAsString(AppointmentStatus.AGENDADA));
        assertEquals("\"REALIZADA\"", objectMapper.writeValueAsString(AppointmentStatus.REALIZADA));
        assertEquals("\"CANCELADA\"", objectMapper.writeValueAsString(AppointmentStatus.CANCELADA));
    }

    @Test
    void deserializesDocumentedJsonIntoTheImmutableContract() throws Exception {
        String json = """
                {
                  "eventVersion": 1,
                  "messageId": "2c5c5f9e-f5a6-45e3-bb88-d2ea9efc1b5e",
                  "eventType": "CONSULTA_EDITADA",
                  "occurredAt": "2026-09-13T12:00:00Z",
                  "appointmentId": "6c3d9f87-62f4-4bcb-878b-9f5b4d75b43c",
                  "patientId": "4e4f6e1e-f3c8-4fd6-82c4-0fc7937d1e2f",
                  "doctorId": "b6a1db6d-b3da-4fb5-8f1b-6f2d6a5b0c19",
                  "scheduledAt": "2026-09-20T14:00:00Z",
                  "status": "CANCELADA",
                  "notes": "Retorno"
                }
                """;

        AppointmentEvent event = objectMapper.readValue(json, AppointmentEvent.class);

        assertEquals(1, event.eventVersion());
        assertEquals(EventType.CONSULTA_EDITADA, event.eventType());
        assertEquals(AppointmentStatus.CANCELADA, event.status());
        assertEquals(Instant.parse("2026-09-20T14:00:00Z"), event.scheduledAt());
    }
}
