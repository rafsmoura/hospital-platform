package com.fiap.hospital.scheduling.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
public class RabbitTopologyConfiguration {

    @Bean
    public TopicExchange appointmentExchange() {
        return new TopicExchange("hospital.appointments", true, false);
    }

    @Bean
    public DirectExchange appointmentDeadLetterExchange() {
        return new DirectExchange("hospital.appointments.dlx", true, false);
    }

    @Bean
    public Queue historyQueue() {
        return QueueBuilder.durable("hospital.history.appointments")
                .deadLetterExchange("hospital.appointments.dlx")
                .build();
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable("hospital.notifications.appointments")
                .deadLetterExchange("hospital.appointments.dlx")
                .build();
    }

    @Bean
    public Queue historyDlq() {
        return QueueBuilder.durable("hospital.history.appointments.dlq").build();
    }

    @Bean
    public Queue notificationDlq() {
        return QueueBuilder.durable("hospital.notifications.appointments.dlq").build();
    }

    @Bean
    public Binding historyCreatedBinding(TopicExchange appointmentExchange,
                                        @Qualifier("historyQueue") Queue historyQueue) {
        return BindingBuilder.bind(historyQueue).to(appointmentExchange).with("appointment.created");
    }

    @Bean
    public Binding historyEditedBinding(TopicExchange appointmentExchange,
                                       @Qualifier("historyQueue") Queue historyQueue) {
        return BindingBuilder.bind(historyQueue).to(appointmentExchange).with("appointment.edited");
    }

    @Bean
    public Binding notificationCreatedBinding(TopicExchange appointmentExchange,
                                             @Qualifier("notificationQueue") Queue notificationQueue) {
        return BindingBuilder.bind(notificationQueue).to(appointmentExchange).with("appointment.created");
    }

    @Bean
    public Binding notificationEditedBinding(TopicExchange appointmentExchange,
                                            @Qualifier("notificationQueue") Queue notificationQueue) {
        return BindingBuilder.bind(notificationQueue).to(appointmentExchange).with("appointment.edited");
    }

    @Bean
    public Binding historyCreatedDlqBinding(DirectExchange appointmentDeadLetterExchange,
                                           @Qualifier("historyDlq") Queue historyDlq) {
        return BindingBuilder.bind(historyDlq).to(appointmentDeadLetterExchange).with("appointment.created");
    }

    @Bean
    public Binding historyEditedDlqBinding(DirectExchange appointmentDeadLetterExchange,
                                          @Qualifier("historyDlq") Queue historyDlq) {
        return BindingBuilder.bind(historyDlq).to(appointmentDeadLetterExchange).with("appointment.edited");
    }

    @Bean
    public Binding notificationCreatedDlqBinding(DirectExchange appointmentDeadLetterExchange,
                                                @Qualifier("notificationDlq") Queue notificationDlq) {
        return BindingBuilder.bind(notificationDlq).to(appointmentDeadLetterExchange).with("appointment.created");
    }

    @Bean
    public Binding notificationEditedDlqBinding(DirectExchange appointmentDeadLetterExchange,
                                               @Qualifier("notificationDlq") Queue notificationDlq) {
        return BindingBuilder.bind(notificationDlq).to(appointmentDeadLetterExchange).with("appointment.edited");
    }
}
