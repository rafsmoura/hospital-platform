package com.fiap.hospital.scheduling.appointment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.hospital.contract.AppointmentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AppointmentCreationControllerIntegrationTest {

    private static final UUID PATIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID DOCTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private AppointmentRepository appointments;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void nurseCreatesScheduledAppointmentWithGeneratedId() throws Exception {
        String request = request(PATIENT_ID, DOCTOR_ID, Instant.now().plusSeconds(3600), null, "Retorno");

        mockMvc.perform(post("/api/v1/appointments")
                        .with(httpBasic("enfermeiro", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.patientId").value(PATIENT_ID.toString()))
                .andExpect(jsonPath("$.doctorId").value(DOCTOR_ID.toString()))
                .andExpect(jsonPath("$.status").value("AGENDADA"))
                .andExpect(jsonPath("$.notes").value("Retorno"));

        assertThat(appointments.findAll()).anyMatch(appointment ->
                appointment.getPatientId().equals(PATIENT_ID)
                        && appointment.getDoctorId().equals(DOCTOR_ID)
                        && appointment.getStatus() == AppointmentStatus.AGENDADA);
    }

    @Test
    void unauthenticatedClientCannotCreateAppointment() throws Exception {
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(PATIENT_ID, DOCTOR_ID, Instant.now().plusSeconds(3600), null, null)))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"medico", "paciente", "admin"})
    void rolesWithoutCreationPermissionReceiveForbidden(String username) throws Exception {
        mockMvc.perform(post("/api/v1/appointments")
                        .with(httpBasic(username, "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(PATIENT_ID, DOCTOR_ID, Instant.now().plusSeconds(3600), null, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void pastDateIsRejectedWithoutPersisting() throws Exception {
        UUID patientId = ADMIN_ID;
        long before = appointments.count();

        mockMvc.perform(post("/api/v1/appointments")
                        .with(httpBasic("enfermeiro", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(patientId, DOCTOR_ID, Instant.parse("2020-01-01T00:00:00Z"), null, null)))
                .andExpect(status().isBadRequest());

        assertThat(appointments.count()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"REALIZADA", "CANCELADA", "INVALIDA"})
    void invalidStatusIsRejectedWithoutPersisting(String statusValue) throws Exception {
        long before = appointments.count();

        mockMvc.perform(post("/api/v1/appointments")
                        .with(httpBasic("enfermeiro", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(PATIENT_ID, DOCTOR_ID, Instant.now().plusSeconds(3600), statusValue, null)))
                .andExpect(status().isBadRequest());

        assertThat(appointments.count()).isEqualTo(before);
    }

    @Test
    void invalidRelationshipsAndMissingFieldsAreRejectedWithoutPersisting() throws Exception {
        long before = appointments.count();

        mockMvc.perform(post("/api/v1/appointments")
                        .with(httpBasic("enfermeiro", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(ADMIN_ID, PATIENT_ID, Instant.now().plusSeconds(3600), null, null)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/appointments")
                        .with(httpBasic("enfermeiro", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(PATIENT_ID, PATIENT_ID, Instant.now().plusSeconds(3600), null, null)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/appointments")
                        .with(httpBasic("enfermeiro", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorId\":\"" + DOCTOR_ID + "\",\"status\":\"AGENDADA\"}"))
                .andExpect(status().isBadRequest());

        assertThat(appointments.count()).isEqualTo(before);
    }

    private String request(UUID patientId, UUID doctorId, Instant scheduledAt, String status, String notes)
            throws Exception {
        return objectMapper.writeValueAsString(new CreateAppointmentRequest(patientId, doctorId, scheduledAt, status, notes));
    }
}
