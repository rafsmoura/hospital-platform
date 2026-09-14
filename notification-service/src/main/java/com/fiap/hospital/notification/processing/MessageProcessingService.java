package com.fiap.hospital.notification.processing;

import com.fiap.hospital.contract.AppointmentEvent;
import com.fiap.hospital.contract.AppointmentStatus;
import com.fiap.hospital.notification.persistence.NotificationLog;
import com.fiap.hospital.notification.persistence.NotificationLogRepository;
import com.fiap.hospital.notification.persistence.NotificationProcessedMessage;
import com.fiap.hospital.notification.persistence.NotificationProcessedMessageRepository;
import com.fiap.hospital.notification.persistence.NotificationProcessingFailure;
import com.fiap.hospital.notification.persistence.NotificationProcessingFailureRepository;
import com.fiap.hospital.notification.persistence.NotificationStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class MessageProcessingService {

    private final NotificationLogRepository notificationLogs;
    private final NotificationProcessedMessageRepository processedMessages;
    private final NotificationProcessingFailureRepository failures;
    private final Clock clock;

    @Autowired
    public MessageProcessingService(NotificationLogRepository notificationLogs,
                                    NotificationProcessedMessageRepository processedMessages,
                                    NotificationProcessingFailureRepository failures) {
        this(notificationLogs, processedMessages, failures, Clock.systemUTC());
    }

    MessageProcessingService(NotificationLogRepository notificationLogs,
                             NotificationProcessedMessageRepository processedMessages,
                             NotificationProcessingFailureRepository failures,
                             Clock clock) {
        this.notificationLogs = notificationLogs;
        this.processedMessages = processedMessages;
        this.failures = failures;
        this.clock = clock;
    }

    @Transactional
    public void process(AppointmentEvent event) {
        if (processedMessages.existsById(event.messageId())) {
            return;
        }

        Instant now = clock.instant();
        if (event.scheduledAt().isAfter(now)) {
            NotificationStatus status = event.status() == AppointmentStatus.CANCELADA
                    ? NotificationStatus.SKIPPED_CANCELLED : NotificationStatus.SENT;
            notificationLogs.save(new NotificationLog(event.messageId(), event.appointmentId(), event.patientId(),
                    event.eventType(), event.scheduledAt(), status, null, now));
        }
        processedMessages.save(new NotificationProcessedMessage(event.messageId(), now));
    }

    @Transactional
    public void recordFailure(java.util.UUID messageId, String reason, String payload) {
        failures.save(new NotificationProcessingFailure(messageId, reason, payload, clock.instant()));
    }
}
