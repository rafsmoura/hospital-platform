package com.fiap.hospital.notification.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class NotificationRabbitTopologyConfiguration {

    @Bean
    TopicExchange appointmentExchange() {
        return new TopicExchange("hospital.appointments", true, false);
    }

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
    Binding notificationCreatedBinding(TopicExchange appointmentExchange,
                                      @Qualifier("notificationAppointmentQueue") Queue notificationAppointmentQueue) {
        return BindingBuilder.bind(notificationAppointmentQueue).to(appointmentExchange).with("appointment.created");
    }

    @Bean
    Binding notificationEditedBinding(TopicExchange appointmentExchange,
                                      @Qualifier("notificationAppointmentQueue") Queue notificationAppointmentQueue) {
        return BindingBuilder.bind(notificationAppointmentQueue).to(appointmentExchange).with("appointment.edited");
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

    @Bean
    RetryTemplate notificationRetryTemplate(@Value("${notification.max-retries:3}") int maxRetries) {
        RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(new SimpleRetryPolicy(maxRetries + 1));
        FixedBackOffPolicy backOff = new FixedBackOffPolicy();
        backOff.setBackOffPeriod(0);
        template.setBackOffPolicy(backOff);
        return template;
    }
}
