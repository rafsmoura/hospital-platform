package com.fiap.hospital.scheduling.messaging;

import com.fiap.hospital.contract.EventType;
import com.fiap.hospital.scheduling.outbox.OutboxEvent;
import com.fiap.hospital.scheduling.outbox.OutboxEventRepository;
import com.fiap.hospital.scheduling.outbox.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPublisherIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");

    @Test
    void confirmedPublicationMarksExistingOutboxRowPublishedAndKeepsMessageId() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        UUID messageId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), messageId, EventType.CONSULTA_CRIADA,
                "{\"messageId\":\"" + messageId + "\"}", "appointment.created", NOW);
        when(repository.findReady(eq(NOW))).thenReturn(List.of(event));
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(4);
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, "confirmed"));
            return null;
        }).when(rabbitTemplate).convertAndSend(eq("hospital.appointments"), eq("appointment.created"),
                eq(event.getPayload()), any(MessagePostProcessor.class), any(CorrelationData.class));

        new OutboxPublisher(repository, rabbitTemplate, Clock.fixed(NOW, ZoneOffset.UTC), 1000)
                .publishPending();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getMessageId()).isEqualTo(messageId);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getPublishedAt()).isEqualTo(NOW);
        verify(repository).save(event);
    }

    @Test
    void unavailableBrokerMarksFailureWithRetryMetadataAndStableMessageId() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        UUID messageId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), messageId, EventType.CONSULTA_EDITADA,
                "{\"messageId\":\"" + messageId + "\"}", "appointment.edited", NOW);
        when(repository.findReady(eq(NOW))).thenReturn(List.of(event));
        doThrow(new AmqpException("broker unavailable")).when(rabbitTemplate).convertAndSend(
                eq("hospital.appointments"), eq("appointment.edited"), eq(event.getPayload()),
                any(MessagePostProcessor.class), any(CorrelationData.class));

        new OutboxPublisher(repository, rabbitTemplate, Clock.fixed(NOW, ZoneOffset.UTC), 1000)
                .publishPending();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getMessageId()).isEqualTo(messageId);
        assertThat(event.getLastError()).contains("broker unavailable");
        assertThat(event.getNextAttemptAt()).isAfter(NOW);
        assertThat(event.getPublishedAt()).isNull();
        verify(repository).save(event);
    }

    @Test
    void returnedPublicationIsNotMarkedAsPublished() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        UUID messageId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), messageId, EventType.CONSULTA_CRIADA,
                "{}", "appointment.created", NOW);
        when(repository.findReady(eq(NOW))).thenReturn(List.of(event));
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(4);
            correlationData.setReturned(new ReturnedMessage(
                    new Message("{}".getBytes(), new MessageProperties()), 312, "NO_ROUTE",
                    "hospital.appointments", "appointment.created"));
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, "confirmed"));
            return null;
        }).when(rabbitTemplate).convertAndSend(eq("hospital.appointments"), eq("appointment.created"),
                eq(event.getPayload()), any(MessagePostProcessor.class), any(CorrelationData.class));

        new OutboxPublisher(repository, rabbitTemplate, Clock.fixed(NOW, ZoneOffset.UTC), 1000)
                .publishPending();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getLastError()).contains("NO_ROUTE");
        verify(repository).save(event);
    }
}
