package com.fiap.hospital.scheduling.messaging;

import com.fiap.hospital.scheduling.outbox.OutboxEvent;
import com.fiap.hospital.scheduling.outbox.OutboxEventRepository;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxPublisher {

    private static final String APPOINTMENT_EXCHANGE = "hospital.appointments";
    private static final long MAX_BACKOFF_SECONDS = 300;

    private final OutboxEventRepository outboxEvents;
    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;
    private final long confirmationTimeoutMillis;

    @Autowired
    public OutboxPublisher(OutboxEventRepository outboxEvents, RabbitTemplate rabbitTemplate,
                           @Value("${outbox.confirmation-timeout-ms:5000}") long confirmationTimeoutMillis) {
        this(outboxEvents, rabbitTemplate, Clock.systemUTC(), confirmationTimeoutMillis);
    }

    OutboxPublisher(OutboxEventRepository outboxEvents, RabbitTemplate rabbitTemplate,
                    Clock clock, long confirmationTimeoutMillis) {
        this.outboxEvents = outboxEvents;
        this.rabbitTemplate = rabbitTemplate;
        this.clock = clock;
        this.confirmationTimeoutMillis = confirmationTimeoutMillis;
    }

    @Scheduled(fixedDelayString = "${outbox.relay-delay-ms:1000}")
    public void publishPending() {
        Instant now = clock.instant();
        outboxEvents.findReady(now).forEach(event -> publish(event, now));
    }

    private void publish(OutboxEvent event, Instant now) {
        event.recordAttempt();
        try {
            CorrelationData correlationData = new CorrelationData(event.getMessageId().toString());
            rabbitTemplate.convertAndSend(APPOINTMENT_EXCHANGE, event.getRoutingKey(), event.getPayload(),
                    (MessagePostProcessor) message -> {
                        message.getMessageProperties().setMessageId(event.getMessageId().toString());
                        message.getMessageProperties().setContentType("application/json");
                        return message;
                    }, correlationData);
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(confirmationTimeoutMillis, TimeUnit.MILLISECONDS);
            if (confirm == null || !confirm.isAck()) {
                throw new IllegalStateException(confirm == null ? "publisher confirmation missing" : confirm.getReason());
            }
            event.markPublished(now);
        } catch (Exception exception) {
            event.markFailed(errorMessage(exception), now.plus(retryDelay(event.getAttempts())));
        }
        outboxEvents.save(event);
    }

    private Duration retryDelay(int attempts) {
        long seconds = Math.min(1L << Math.min(attempts - 1, 8), MAX_BACKOFF_SECONDS);
        return Duration.ofSeconds(seconds);
    }

    private String errorMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
