package com.fiap.hospital.history.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HistoryRabbitTopologyConfiguration {

    @Bean
    Queue historyAppointmentQueue() {
        return QueueBuilder.durable("hospital.history.appointments")
                .deadLetterExchange("hospital.appointments.dlx")
                .build();
    }

    @Bean
    Queue historyAppointmentDlq() {
        return QueueBuilder.durable("hospital.history.appointments.dlq").build();
    }

    @Bean
    DirectExchange appointmentDeadLetterExchange() {
        return new DirectExchange("hospital.appointments.dlx", true, false);
    }

    @Bean
    Binding historyCreatedDlqBinding(DirectExchange appointmentDeadLetterExchange,
                                    @Qualifier("historyAppointmentDlq") Queue historyAppointmentDlq) {
        return BindingBuilder.bind(historyAppointmentDlq).to(appointmentDeadLetterExchange).with("appointment.created");
    }

    @Bean
    Binding historyEditedDlqBinding(DirectExchange appointmentDeadLetterExchange,
                                   @Qualifier("historyAppointmentDlq") Queue historyAppointmentDlq) {
        return BindingBuilder.bind(historyAppointmentDlq).to(appointmentDeadLetterExchange).with("appointment.edited");
    }
}
