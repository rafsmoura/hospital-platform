package com.fiap.hospital.scheduling.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.hospital.contract.AppointmentStatus;
import com.fiap.hospital.contract.EventType;
import com.fiap.hospital.scheduling.appointment.Appointment;
import com.fiap.hospital.scheduling.appointment.AppointmentRepository;
import com.fiap.hospital.scheduling.appointment.UpdateAppointmentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OutboxEventIntegrationTest {

    private static final UUID PATIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID DOCTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private AppointmentRepository appointments;

    @Autowired
    private OutboxEventRepository outboxEvents;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void successfulCreateAndEditEachProduceOneVersionedOutboxMessage() throws Exception {
        long before = outboxEvents.count();
        Set<UUID> existingMessages = outboxEvents.findAll().stream()
                .map(OutboxEvent::getMessageId)
                .collect(java.util.stream.Collectors.toSet());
        String createRequest = "{\"patientId\":\"" + PATIENT_ID + "\",\"doctorId\":\"" + DOCTOR_ID
                + "\",\"scheduledAt\":\"2030-01-10T14:30:00Z\",\"notes\":\"Retorno\"}";

        mockMvc.perform(post("/api/v1/appointments")
                        .with(httpBasic("enfermeiro", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated());

        OutboxEvent created = outboxEvents.findAll().stream()
                .filter(event -> event.getEventType() == EventType.CONSULTA_CRIADA
                        && !existingMessages.contains(event.getMessageId()))
                .findFirst()
                .orElseThrow();
        JsonNode createdPayload = objectMapper.readTree(created.getPayload());
        UUID appointmentId = UUID.fromString(createdPayload.get("appointmentId").asText());
        assertThat(outboxEvents.count()).isEqualTo(before + 1);
        assertThat(created.getMessageId()).isEqualTo(UUID.fromString(createdPayload.get("messageId").asText()));
        assertThat(created.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(created.getRoutingKey()).isEqualTo("appointment.created");
        assertThat(createdPayload.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(createdPayload.get("eventType").asText()).isEqualTo("CONSULTA_CRIADA");
        assertThat(createdPayload.get("patientId").asText()).isEqualTo(PATIENT_ID.toString());
        assertThat(createdPayload.get("status").asText()).isEqualTo("AGENDADA");
        assertThat(createdPayload.get("scheduledAt").asText()).isEqualTo("2030-01-10T14:30:00Z");

        Appointment appointment = appointments.findById(appointmentId).orElseThrow();
        Set<UUID> messagesBeforeEdit = outboxEvents.findAll().stream()
                .map(OutboxEvent::getMessageId)
                .collect(java.util.stream.Collectors.toSet());
        String updateRequest = objectMapper.writeValueAsString(new UpdateAppointmentRequest(
                PATIENT_ID, DOCTOR_ID, Instant.parse("2030-01-11T14:30:00Z"),
                AppointmentStatus.AGENDADA.name(), "Editada", appointment.getVersion()));
        mockMvc.perform(put("/api/v1/appointments/" + appointmentId)
                        .with(httpBasic("medico", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk());

        OutboxEvent edited = outboxEvents.findAll().stream()
                .filter(event -> event.getEventType() == EventType.CONSULTA_EDITADA
                        && !messagesBeforeEdit.contains(event.getMessageId()))
                .findFirst().orElseThrow();
        JsonNode editedPayload = objectMapper.readTree(edited.getPayload());
        assertThat(outboxEvents.count()).isEqualTo(before + 2);
        assertThat(edited.getMessageId()).isNotEqualTo(created.getMessageId());
        assertThat(edited.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(edited.getRoutingKey()).isEqualTo("appointment.edited");
        assertThat(editedPayload.get("eventType").asText()).isEqualTo("CONSULTA_EDITADA");
        assertThat(editedPayload.get("appointmentId").asText()).isEqualTo(appointmentId.toString());
        assertThat(editedPayload.get("notes").asText()).isEqualTo("Editada");
    }

    @Test
    void invalidAndUnknownMutationsDoNotCreateOutboxMessages() throws Exception {
        long before = outboxEvents.count();
        mockMvc.perform(post("/api/v1/appointments")
                        .with(httpBasic("enfermeiro", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"" + PATIENT_ID + "\",\"scheduledAt\":\"2020-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/appointments/" + UUID.randomUUID())
                        .with(httpBasic("medico", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"" + PATIENT_ID + "\",\"scheduledAt\":\"2030-01-10T14:30:00Z\",\"status\":\"AGENDADA\",\"version\":0}"))
                .andExpect(status().isNotFound());

        assertThat(outboxEvents.count()).isEqualTo(before);
    }

}
