package com.fiap.hospital.scheduling.appointment;

import com.fiap.hospital.contract.AppointmentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AppointmentQueryControllerIntegrationTest {

    private static final UUID PATIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID DOCTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private AppointmentRepository appointments;

    @Test
    void staffReadsAppointmentWithoutCredentialFields() throws Exception {
        Appointment appointment = create();

        mockMvc.perform(get("/api/v1/appointments/" + appointment.getId())
                        .with(httpBasic("medico", "password")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(appointment.getId().toString()))
                .andExpect(jsonPath("$.patientId").value(PATIENT_ID.toString()))
                .andExpect(jsonPath("$.status").value("AGENDADA"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password_hash").doesNotExist());
    }

    @Test
    void unauthenticatedReadReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/appointments/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patientCannotUseStaffReadEndpoint() throws Exception {
        Appointment appointment = create();

        mockMvc.perform(get("/api/v1/appointments/" + appointment.getId())
                        .with(httpBasic("paciente", "password")))
                .andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(appointment.getId().toString()))));
    }

    @Test
    void staffUnknownIdReturnsGeneric404() throws Exception {
        String unknownId = UUID.randomUUID().toString();

        mockMvc.perform(get("/api/v1/appointments/" + unknownId)
                        .with(httpBasic("enfermeiro", "password")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Appointment not found"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(unknownId))));
    }

    @Test
    void staffInvalidUuidReturnsBadRequestWithoutStackTrace() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/appointments/not-a-uuid")
                        .with(httpBasic("enfermeiro", "password")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid request parameters"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("trace", "stackTrace");
    }

    private Appointment create() {
        Appointment appointment = appointments.saveAndFlush(new Appointment(
                UUID.randomUUID(), PATIENT_ID, DOCTOR_ID, Instant.now().plusSeconds(3600),
                AppointmentStatus.AGENDADA, "Confidential note"));
        assertThat(appointment.getId()).isNotNull();
        return appointment;
    }
}
