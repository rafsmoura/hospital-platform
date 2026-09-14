package com.fiap.hospital.history.messaging;

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
public class HistoryRabbitTopologyConfiguration {

    @Bean
    TopicExchange appointmentExchange() {
        return new TopicExchange("hospital.appointments", true, false);
    }

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
    Binding historyCreatedBinding(TopicExchange appointmentExchange,
                                  @Qualifier("historyAppointmentQueue") Queue historyAppointmentQueue) {
        return BindingBuilder.bind(historyAppointmentQueue).to(appointmentExchange).with("appointment.created");
    }

    @Bean
    Binding historyEditedBinding(TopicExchange appointmentExchange,
                                 @Qualifier("historyAppointmentQueue") Queue historyAppointmentQueue) {
        return BindingBuilder.bind(historyAppointmentQueue).to(appointmentExchange).with("appointment.edited");
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

    @Bean
    RetryTemplate historyRetryTemplate(@Value("${history.max-retries:3}") int maxRetries) {
        RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(new SimpleRetryPolicy(maxRetries + 1));
        FixedBackOffPolicy backOff = new FixedBackOffPolicy();
        backOff.setBackOffPeriod(0);
        template.setBackOffPolicy(backOff);
        return template;
    }
}
