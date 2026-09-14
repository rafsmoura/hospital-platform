package com.fiap.hospital.scheduling.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitTopologyConfigurationTest {

    private final RabbitTopologyConfiguration configuration = new RabbitTopologyConfiguration();

    @Test
    void declaresDurableAppointmentTopologyWithIndependentConsumerQueues() {
        TopicExchange exchange = configuration.appointmentExchange();
        DirectExchange deadLetterExchange = configuration.appointmentDeadLetterExchange();
        Queue history = configuration.historyQueue();
        Queue notifications = configuration.notificationQueue();

        assertThat(exchange.getName()).isEqualTo("hospital.appointments");
        assertThat(exchange.isDurable()).isTrue();
        assertThat(exchange.getType()).isEqualTo("topic");
        assertThat(deadLetterExchange.getName()).isEqualTo("hospital.appointments.dlx");
        assertThat(deadLetterExchange.isDurable()).isTrue();
        assertThat(history.getName()).isEqualTo("hospital.history.appointments");
        assertThat(notifications.getName()).isEqualTo("hospital.notifications.appointments");
        assertThat(history.isDurable()).isTrue();
        assertThat(notifications.isDurable()).isTrue();
        assertThat(history.getArguments()).containsEntry("x-dead-letter-exchange", "hospital.appointments.dlx");
        assertThat(notifications.getArguments()).containsEntry("x-dead-letter-exchange", "hospital.appointments.dlx");
    }

    @Test
    void bindsBothEventRoutingKeysToEachConsumerAndItsDeadLetterQueue() {
        TopicExchange exchange = configuration.appointmentExchange();
        DirectExchange deadLetterExchange = configuration.appointmentDeadLetterExchange();
        List<Binding> bindings = List.of(
                configuration.historyCreatedBinding(exchange, configuration.historyQueue()),
                configuration.historyEditedBinding(exchange, configuration.historyQueue()),
                configuration.notificationCreatedBinding(exchange, configuration.notificationQueue()),
                configuration.notificationEditedBinding(exchange, configuration.notificationQueue()),
                configuration.historyCreatedDlqBinding(deadLetterExchange, configuration.historyDlq()),
                configuration.historyEditedDlqBinding(deadLetterExchange, configuration.historyDlq()),
                configuration.notificationCreatedDlqBinding(deadLetterExchange, configuration.notificationDlq()),
                configuration.notificationEditedDlqBinding(deadLetterExchange, configuration.notificationDlq()));

        assertThat(bindings).extracting(Binding::getDestination)
                .containsExactlyInAnyOrder(
                        "hospital.history.appointments", "hospital.history.appointments",
                        "hospital.notifications.appointments", "hospital.notifications.appointments",
                        "hospital.history.appointments.dlq", "hospital.history.appointments.dlq",
                        "hospital.notifications.appointments.dlq", "hospital.notifications.appointments.dlq");
        assertThat(bindings).extracting(Binding::getRoutingKey)
                .containsExactlyInAnyOrder(
                        "appointment.created", "appointment.edited", "appointment.created", "appointment.edited",
                        "appointment.created", "appointment.edited", "appointment.created", "appointment.edited");
    }
}
