package com.fiap.hospital.history.graphql;

import com.fiap.hospital.contract.AppointmentEvent;
import com.fiap.hospital.contract.AppointmentStatus;
import com.fiap.hospital.contract.EventType;
import com.fiap.hospital.history.projection.HistoryAppointment;
import com.fiap.hospital.history.projection.HistoryAppointmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HistoryGraphQlIntegrationTest {

    private static final UUID PATIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID OTHER_PATIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID APPOINTMENT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OTHER_APPOINTMENT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HistoryAppointmentRepository appointments;

    @BeforeEach
    void seedProjection() {
        appointments.deleteAll();
        appointments.save(new HistoryAppointment(event(APPOINTMENT_ID, PATIENT_ID,
                Instant.parse("2026-09-20T14:00:00Z"), "Primeira"), Instant.parse("2026-09-14T12:00:00Z")));
        appointments.save(new HistoryAppointment(event(OTHER_APPOINTMENT_ID, OTHER_PATIENT_ID,
                Instant.parse("2026-09-22T14:00:00Z"), "Outra"), Instant.parse("2026-09-14T12:01:00Z")));
    }

    @Test
    void staffCanQueryFullFutureAndDescendingHistory() throws Exception {
        String query = "query($patientId: ID!, $from: String!) { patientHistory(patientId: $patientId, futureOnly: true, from: $from, sort: DESC) { id patientId scheduledAt notes } }";
        String body = "{\"query\":\"" + query + "\",\"variables\":{\"patientId\":\""
                + PATIENT_ID + "\",\"from\":\"2026-09-19T00:00:00Z\"}}";

        mockMvc.perform(post("/graphql").with(httpBasic("medico", "password"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.patientHistory[0].id").value(APPOINTMENT_ID.toString()))
                .andExpect(jsonPath("$.data.patientHistory[0].patientId").value(PATIENT_ID.toString()))
                .andExpect(jsonPath("$.data.patientHistory[0].scheduledAt").value("2026-09-20T14:00:00Z"))
                .andExpect(jsonPath("$.data.patientHistory[0].notes").value("Primeira"));
    }

    @Test
    void patientCanReadOwnHistoryButTamperingReturnsAuthorizationErrorWithoutOtherData() throws Exception {
        String query = "query($patientId: ID!) { patientHistory(patientId: $patientId) { id patientId notes } }";
        String ownBody = "{\"query\":\"" + query + "\",\"variables\":{\"patientId\":\""
                + PATIENT_ID + "\"}}";
        String otherBody = "{\"query\":\"" + query + "\",\"variables\":{\"patientId\":\""
                + OTHER_PATIENT_ID + "\"}}";

        mockMvc.perform(post("/graphql").with(httpBasic("paciente", "password"))
                        .contentType(MediaType.APPLICATION_JSON).content(ownBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patientHistory[0].patientId").value(PATIENT_ID.toString()));
        mockMvc.perform(post("/graphql").with(httpBasic("paciente", "password"))
                        .contentType(MediaType.APPLICATION_JSON).content(otherBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patientHistory").doesNotExist())
                .andExpect(jsonPath("$.errors[0].extensions.classification").value("FORBIDDEN"));
    }

    @Test
    void validEmptyHistoryReturnsEmptyListAndUnknownAppointmentReturnsNull() throws Exception {
        String emptyQuery = "{ patientHistory(patientId: \\\"" + PATIENT_ID
                + "\\\", futureOnly: true, from: \\\"2035-01-01T00:00:00Z\\\") { id } }";
        String unknownQuery = "{ appointment(id: \\\"dddddddd-dddd-dddd-dddd-dddddddddddd\\\") { id patientId } }";

        mockMvc.perform(post("/graphql").with(httpBasic("enfermeiro", "password"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"" + emptyQuery + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.patientHistory").isArray())
                .andExpect(jsonPath("$.data.patientHistory").isEmpty());
        mockMvc.perform(post("/graphql").with(httpBasic("medico", "password"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"" + unknownQuery + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.appointment").doesNotExist());
    }

    @Test
    void invalidPatientIdReturnsBadRequestWithoutStackTrace() throws Exception {
        assertInvalidRequest(
                "{ patientHistory(patientId: \"not-a-uuid\") { id } }",
                "patientHistory");
    }

    @Test
    void invalidFromReturnsBadRequestWithoutStackTrace() throws Exception {
        assertInvalidRequest(
                "{ patientHistory(patientId: \"" + PATIENT_ID + "\", from: \"not-an-instant\") { id } }",
                "patientHistory");
    }

    @Test
    void invalidAppointmentIdReturnsBadRequestWithoutStackTrace() throws Exception {
        assertInvalidRequest(
                "{ appointment(id: \"not-a-uuid\") { id } }",
                "appointment");
    }

    @Test
    void graphqlEndpointRequiresAuthentication() throws Exception {
        String body = "{\"query\":\"{ patientHistory(patientId: \\\"" + PATIENT_ID + "\\\") { id } }\"}";

        mockMvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    private void assertInvalidRequest(String query, String field) throws Exception {
        String body = "{\"query\":\"" + query.replace("\"", "\\\"") + "\"}";

        MvcResult result = mockMvc.perform(post("/graphql").with(httpBasic("medico", "password"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors[0].message").value("Invalid request parameters"))
                .andExpect(jsonPath("$.errors[0].extensions.classification").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.data." + field).doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("trace", "stackTrace");
    }

    private AppointmentEvent event(UUID appointmentId, UUID patientId, Instant scheduledAt, String notes) {
        return new AppointmentEvent(1, UUID.randomUUID(), EventType.CONSULTA_CRIADA,
                Instant.parse("2026-09-14T10:00:00Z"), appointmentId, patientId,
                UUID.fromString("00000000-0000-0000-0000-000000000002"), scheduledAt,
                AppointmentStatus.AGENDADA, notes);
    }
}
