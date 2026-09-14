package com.fiap.hospital.history.projection;

import com.fiap.hospital.contract.AppointmentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class HistoryProjectionService {

    private final HistoryAppointmentRepository appointments;
    private final HistoryProcessedMessageRepository processedMessages;
    private final Clock clock;

    public HistoryProjectionService(HistoryAppointmentRepository appointments,
                                    HistoryProcessedMessageRepository processedMessages) {
        this.appointments = appointments;
        this.processedMessages = processedMessages;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public void project(AppointmentEvent event) {
        if (processedMessages.existsById(event.messageId())) {
            return;
        }

        HistoryAppointment projection = appointments.findById(event.appointmentId()).orElse(null);
        if (projection == null) {
            appointments.save(new HistoryAppointment(event, clock.instant()));
        } else if (event.occurredAt().isAfter(projection.getOccurredAt())) {
            projection.apply(event, clock.instant());
            appointments.save(projection);
        }
        processedMessages.save(new HistoryProcessedMessage(event.messageId(), clock.instant()));
    }
}
