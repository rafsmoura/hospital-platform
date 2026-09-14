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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AppointmentUpdateControllerIntegrationTest {

    private static final UUID PATIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID DOCTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private AppointmentRepository appointments;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void doctorAndNurseCanEditScheduledAppointments() throws Exception {
        Appointment doctorAppointment = create("Doctor original");
        mockMvc.perform(put("/api/v1/appointments/" + doctorAppointment.getId())
                        .with(httpBasic("medico", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Doctor updated", AppointmentStatus.AGENDADA, doctorAppointment.getVersion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("Doctor updated"))
                .andExpect(jsonPath("$.status").value("AGENDADA"))
                .andExpect(jsonPath("$.version").value(1));

        Appointment nurseAppointment = create("Nurse original");
        mockMvc.perform(put("/api/v1/appointments/" + nurseAppointment.getId())
                        .with(httpBasic("enfermeiro", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Nurse updated", AppointmentStatus.AGENDADA, nurseAppointment.getVersion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("Nurse updated"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"REALIZADA", "CANCELADA"})
    void finalStatesCanBeSetButCannotBeEdited(String finalStatus) throws Exception {
        Appointment appointment = create("Preserved");

        mockMvc.perform(put("/api/v1/appointments/" + appointment.getId())
                        .with(httpBasic("medico", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Final", AppointmentStatus.valueOf(finalStatus), appointment.getVersion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(finalStatus));
        Appointment finalAppointment = appointments.findById(appointment.getId()).orElseThrow();
        String storedNotes = finalAppointment.getNotes();

        mockMvc.perform(put("/api/v1/appointments/" + appointment.getId())
                        .with(httpBasic("enfermeiro", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Must not replace", AppointmentStatus.AGENDADA, finalAppointment.getVersion())))
                .andExpect(status().isConflict());

        Appointment preserved = appointments.findById(appointment.getId()).orElseThrow();
        assertThat(preserved.getStatus()).isEqualTo(AppointmentStatus.valueOf(finalStatus));
        assertThat(preserved.getNotes()).isEqualTo(storedNotes);
    }

    @Test
    void staleVersionReturnsConflictWithoutOverwritingNewerData() throws Exception {
        Appointment appointment = create("Original");
        long staleVersion = appointment.getVersion();

        mockMvc.perform(put("/api/v1/appointments/" + appointment.getId())
                        .with(httpBasic("medico", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Newer", AppointmentStatus.AGENDADA, staleVersion)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/appointments/" + appointment.getId())
                        .with(httpBasic("enfermeiro", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Stale", AppointmentStatus.AGENDADA, staleVersion)))
                .andExpect(status().isConflict());

        Appointment stored = appointments.findById(appointment.getId()).orElseThrow();
        assertThat(stored.getNotes()).isEqualTo("Newer");
        assertThat(stored.getVersion()).isEqualTo(1);
    }

    @Test
    void invalidStatusAndUnknownAppointmentDoNotMutateData() throws Exception {
        Appointment appointment = create("Original");
        mockMvc.perform(put("/api/v1/appointments/" + appointment.getId())
                        .with(httpBasic("medico", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Invalid", null, appointment.getVersion(), "INVALIDA")))
                .andExpect(status().isBadRequest());
        assertThat(appointments.findById(appointment.getId()).orElseThrow().getNotes()).isEqualTo("Original");

        mockMvc.perform(put("/api/v1/appointments/" + UUID.randomUUID())
                        .with(httpBasic("medico", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Unknown", AppointmentStatus.AGENDADA, 0)))
                .andExpect(status().isNotFound());
    }

    private Appointment create(String notes) {
        return appointments.saveAndFlush(new Appointment(
                UUID.randomUUID(), PATIENT_ID, DOCTOR_ID, Instant.now().plusSeconds(3600),
                AppointmentStatus.AGENDADA, notes));
    }

    private String request(String notes, AppointmentStatus status, long version) throws Exception {
        return request(notes, status, version, status == null ? null : status.name());
    }

    private String request(String notes, AppointmentStatus status, long version, String statusValue) throws Exception {
        return objectMapper.writeValueAsString(new UpdateAppointmentRequest(
                PATIENT_ID, DOCTOR_ID, Instant.now().plusSeconds(3600), statusValue, notes, version));
    }
}
