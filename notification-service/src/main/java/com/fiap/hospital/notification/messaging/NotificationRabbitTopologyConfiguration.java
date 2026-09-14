package com.fiap.hospital.notification.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationRabbitTopologyConfiguration {

    @Bean
    Queue notificationAppointmentQueue() {
        return QueueBuilder.durable("hospital.notifications.appointments")
                .deadLetterExchange("hospital.appointments.dlx")
                .build();
    }

    @Bean
    Queue notificationAppointmentDlq() {
        return QueueBuilder.durable("hospital.notifications.appointments.dlq").build();
    }

    @Bean
    DirectExchange appointmentDeadLetterExchange() {
        return new DirectExchange("hospital.appointments.dlx", true, false);
    }

    @Bean
    Binding notificationCreatedDlqBinding(DirectExchange appointmentDeadLetterExchange,
                                          @Qualifier("notificationAppointmentDlq") Queue notificationAppointmentDlq) {
        return BindingBuilder.bind(notificationAppointmentDlq).to(appointmentDeadLetterExchange).with("appointment.created");
    }

    @Bean
    Binding notificationEditedDlqBinding(DirectExchange appointmentDeadLetterExchange,
                                         @Qualifier("notificationAppointmentDlq") Queue notificationAppointmentDlq) {
        return BindingBuilder.bind(notificationAppointmentDlq).to(appointmentDeadLetterExchange).with("appointment.edited");
    }
}
