package com.fiap.hospital.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.hospital.contract.AppointmentEvent;
import com.fiap.hospital.contract.AppointmentStatus;
import com.fiap.hospital.contract.EventType;
import com.fiap.hospital.history.graphql.AppointmentSort;
import com.fiap.hospital.history.graphql.HistoryGraphQlController;
import com.fiap.hospital.history.graphql.HistoryQueryService;
import com.fiap.hospital.history.projection.AppointmentProjectionConsumer;
import com.fiap.hospital.history.projection.HistoryAppointment;
import com.fiap.hospital.history.projection.HistoryAppointmentRepository;
import com.fiap.hospital.history.projection.HistoryProcessedMessage;
import com.fiap.hospital.history.projection.HistoryProcessedMessageRepository;
import com.fiap.hospital.history.projection.HistoryProcessingFailureRepository;
import com.fiap.hospital.history.security.PatientAccessPolicy;
import com.fiap.hospital.history.user.UserAccount;
import com.fiap.hospital.history.user.UserRepository;
import com.fiap.hospital.notification.messaging.AppointmentNotificationConsumer;
import com.fiap.hospital.notification.persistence.NotificationLog;
import com.fiap.hospital.notification.persistence.NotificationLogRepository;
import com.fiap.hospital.notification.persistence.NotificationProcessedMessage;
import com.fiap.hospital.notification.persistence.NotificationProcessedMessageRepository;
import com.fiap.hospital.notification.persistence.NotificationProcessingFailureRepository;
import com.fiap.hospital.notification.processing.MessageProcessingService;
import com.fiap.hospital.scheduling.appointment.AppointmentRepository;
import com.fiap.hospital.scheduling.appointment.AppointmentResponse;
import com.fiap.hospital.scheduling.appointment.CreateAppointmentRequest;
import com.fiap.hospital.scheduling.appointment.UpdateAppointmentRequest;
import com.fiap.hospital.scheduling.messaging.OutboxPublisher;
import com.fiap.hospital.scheduling.outbox.OutboxEvent;
import com.fiap.hospital.scheduling.outbox.OutboxEventRepository;
import com.fiap.hospital.scheduling.outbox.OutboxStatus;
import com.fiap.hospital.scheduling.SchedulingApplication;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deterministic local E2E fallback. Docker was unavailable, so H2 and in-process
 * consumer delivery exercise the real APIs, outbox relay, event contract and consumers.
 */
@SpringBootTest(classes = SchedulingApplication.class, properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "outbox.relay-delay-ms=3600000",
        "outbox.confirmation-timeout-ms=1000"
})
@AutoConfigureMockMvc
class HospitalFlowE2eTest {

    private static final UUID PATIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID OTHER_PATIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID DOCTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant CREATED_AT = Instant.parse("2030-09-20T14:00:00Z");
    private static final Instant EDITED_AT = Instant.parse("2030-09-21T14:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppointmentRepository appointments;

    @Autowired
    private OutboxEventRepository outboxEvents;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @MockBean
    private OutboxPublisher scheduledRelay;

    @BeforeEach
    void cleanSchedulingState() {
        outboxEvents.deleteAll();
        appointments.deleteAll();
    }

    @Test
    void completeFlowPublishesBothEventsToHistoryAndNotificationConsumers() throws Exception {
        AppointmentResponse created = createAppointment("Initial appointment");
        AppointmentResponse edited = editAppointment(created, "Updated appointment");

        List<OutboxEvent> pending = outboxEvents.findAll();
        assertThat(pending).hasSize(2);
        assertThat(pending).extracting(OutboxEvent::getEventType)
                .containsExactlyInAnyOrder(EventType.CONSULTA_CRIADA, EventType.CONSULTA_EDITADA);

        confirmPublication();
        new OutboxPublisher(outboxEvents, rabbitTemplate, 1000).publishPending();

        List<OutboxEvent> published = outboxEvents.findAll();
        assertThat(published).allMatch(event -> event.getStatus() == OutboxStatus.PUBLISHED);
        assertThat(published).allMatch(event -> event.getMessageId() != null);

        Map<UUID, HistoryAppointment> projections = new HashMap<>();
        int[] historyProcessedCount = {0};
        HistoryAppointmentRepository historyAppointments = mock(HistoryAppointmentRepository.class);
        HistoryProcessedMessageRepository historyMessages = mock(HistoryProcessedMessageRepository.class);
        HistoryProcessingFailureRepository historyFailures = mock(HistoryProcessingFailureRepository.class);
        when(historyAppointments.findById(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(projections.get(invocation.getArgument(0))));
        when(historyAppointments.save(any(HistoryAppointment.class))).thenAnswer(saveTo(projections));
        when(historyMessages.existsById(any(UUID.class)))
                .thenReturn(false);
        when(historyMessages.save(any(HistoryProcessedMessage.class))).thenAnswer(invocation -> {
            historyProcessedCount[0]++;
            return invocation.getArgument(0);
        });

        Map<UUID, NotificationLog> reminders = new HashMap<>();
        Set<UUID> notificationProcessed = new java.util.HashSet<>();
        NotificationLogRepository notificationLogs = mock(NotificationLogRepository.class);
        NotificationProcessedMessageRepository notificationMessages = mock(NotificationProcessedMessageRepository.class);
        NotificationProcessingFailureRepository notificationFailures = mock(NotificationProcessingFailureRepository.class);
        when(notificationLogs.save(any(NotificationLog.class))).thenAnswer(saveTo(reminders));
        when(notificationMessages.existsById(any(UUID.class)))
                .thenAnswer(invocation -> notificationProcessed.contains(invocation.getArgument(0)));
        when(notificationMessages.save(any(NotificationProcessedMessage.class))).thenAnswer(invocation -> {
            notificationProcessed.add(((NotificationProcessedMessage) invocation.getArgument(0)).getMessageId());
            return invocation.getArgument(0);
        });

        AppointmentProjectionConsumer historyConsumer = new AppointmentProjectionConsumer(
                historyAppointments, historyMessages, historyFailures, objectMapper);
        MessageProcessingService processing = new MessageProcessingService(
                notificationLogs, notificationMessages, notificationFailures);
        AppointmentNotificationConsumer notificationConsumer = new AppointmentNotificationConsumer(
                processing, objectMapper, rabbitTemplate);
        Channel channel = mock(Channel.class);

        for (OutboxEvent event : published) {
            AppointmentEvent payload = objectMapper.readValue(event.getPayload(), AppointmentEvent.class);
            Message message = message(payload, published.indexOf(event) + 1L);
            historyConsumer.onMessage(message, channel);
            notificationConsumer.onMessage(message, channel);
        }

        HistoryAppointment projection = projections.get(created.id());
        assertThat(projection.getAppointmentId()).isEqualTo(created.id());
        assertThat(projection.getPatientId()).isEqualTo(PATIENT_ID);
        assertThat(projection.getStatus()).isEqualTo(AppointmentStatus.AGENDADA);
        assertThat(projection.getNotes()).isEqualTo(edited.notes());
        assertThat(historyProcessedCount[0]).isEqualTo(2);
        assertThat(reminders).hasSize(2);
        assertThat(reminders.values()).allMatch(log -> log.getPatientId().equals(PATIENT_ID));
        assertThat(reminders.values()).allMatch(log -> log.getStatus().name().equals("SENT"));
        assertThat(reminders.values()).extracting(NotificationLog::getEventType)
                .containsExactlyInAnyOrder(EventType.CONSULTA_CRIADA, EventType.CONSULTA_EDITADA);
        assertThat(notificationProcessed).hasSize(2);
        verify(channel, times(4)).basicAck(anyLong(), eq(false));
    }

    @Test
    void roleMatrixAllowsExpectedOperationsAndRejectsOtherRoles() throws Exception {
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest(PATIENT_ID, CREATED_AT, "Not authenticated")))
                .andExpect(status().isUnauthorized());

        for (String username : List.of("medico", "paciente", "admin")) {
            mockMvc.perform(post("/api/v1/appointments").with(httpBasic(username, "password"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createRequest(PATIENT_ID, CREATED_AT, "Forbidden")))
                    .andExpect(status().isForbidden());
        }

        AppointmentResponse created = createAppointment("Role matrix");
        mockMvc.perform(get("/api/v1/appointments/" + created.id()).with(httpBasic("medico", "password")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/appointments/" + created.id()).with(httpBasic("enfermeiro", "password")))
                .andExpect(status().isOk());
        for (String username : List.of("paciente", "admin")) {
            mockMvc.perform(get("/api/v1/appointments/" + created.id()).with(httpBasic(username, "password")))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/appointments/" + created.id()))
                .andExpect(status().isUnauthorized());

        AppointmentResponse doctorEdit = editAppointment(created, "Doctor edit");
        editAppointment(doctorEdit, "Nurse edit", "enfermeiro");
        for (String username : List.of("paciente", "admin")) {
            mockMvc.perform(put("/api/v1/appointments/" + created.id()).with(httpBasic(username, "password"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateRequest(created, "Forbidden edit")))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(put("/api/v1/appointments/" + created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest(created, "Not authenticated")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patientGraphQlAdapterRejectsTamperedPatientIdBeforeReadingProjection() {
        UserRepository users = mock(UserRepository.class);
        UserAccount patient = mock(UserAccount.class);
        when(patient.getId()).thenReturn(PATIENT_ID);
        when(users.findByUsernameIgnoreCase("paciente")).thenReturn(Optional.of(patient));
        HistoryAppointmentRepository historyAppointments = mock(HistoryAppointmentRepository.class);
        HistoryQueryService queries = new HistoryQueryService(historyAppointments, new PatientAccessPolicy(users));
        HistoryGraphQlController graphQl = new HistoryGraphQlController(queries);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "paciente", "N/A", List.of(new SimpleGrantedAuthority("ROLE_PACIENTE"))));

        try {
            assertThatThrownBy(() -> graphQl.patientHistory(OTHER_PATIENT_ID.toString(), false, null, AppointmentSort.ASC))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("ownership");
            verify(historyAppointments, never()).findByPatientIdOrderByScheduledAtAsc(OTHER_PATIENT_ID);
            verify(historyAppointments, never()).findByPatientIdOrderByScheduledAtDesc(OTHER_PATIENT_ID);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private AppointmentResponse createAppointment(String notes) throws Exception {
        return objectMapper.readValue(mockMvc.perform(post("/api/v1/appointments")
                        .with(httpBasic("enfermeiro", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest(PATIENT_ID, CREATED_AT, notes)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AGENDADA"))
                .andReturn().getResponse().getContentAsString(), AppointmentResponse.class);
    }

    private AppointmentResponse editAppointment(AppointmentResponse appointment, String notes) throws Exception {
        return editAppointment(appointment, notes, "medico");
    }

    private AppointmentResponse editAppointment(AppointmentResponse appointment, String notes, String username)
            throws Exception {
        return objectMapper.readValue(mockMvc.perform(put("/api/v1/appointments/" + appointment.id())
                        .with(httpBasic(username, "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest(appointment, notes)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), AppointmentResponse.class);
    }

    private String createRequest(UUID patientId, Instant scheduledAt, String notes) throws Exception {
        return objectMapper.writeValueAsString(new CreateAppointmentRequest(
                patientId, DOCTOR_ID, scheduledAt, null, notes));
    }

    private String updateRequest(AppointmentResponse appointment, String notes) throws Exception {
        return objectMapper.writeValueAsString(new UpdateAppointmentRequest(
                PATIENT_ID, DOCTOR_ID, EDITED_AT, "AGENDADA", notes, appointment.version()));
    }

    private void confirmPublication() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, "confirmed"));
            return null;
        }).when(rabbitTemplate).convertAndSend(eq("hospital.appointments"), any(String.class),
                any(String.class), any(org.springframework.amqp.core.MessagePostProcessor.class),
                any(CorrelationData.class));
    }

    private Message message(AppointmentEvent event, long deliveryTag) throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(deliveryTag);
        return new Message(objectMapper.writeValueAsBytes(event), properties);
    }

    private <T> Answer<T> saveTo(Map<UUID, T> values) {
        return invocation -> {
            T value = invocation.getArgument(0);
            if (value instanceof HistoryAppointment appointment) {
                values.put(appointment.getAppointmentId(), value);
            } else if (value instanceof NotificationLog log) {
                values.put(log.getMessageId(), value);
            }
            return value;
        };
    }
}
