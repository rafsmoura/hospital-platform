package com.fiap.hospital.notification.messaging;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class NotificationRabbitTopologyIntegrationTest {

    private static final String USER = "integration";
    private static final String PASSWORD = "integration";
    private static final String EXCHANGE = "hospital.appointments";
    private static final String DLX = "hospital.appointments.dlx";
    private static final String QUEUE = "hospital.notifications.appointments";
    private static final String DLQ = "hospital.notifications.appointments.dlq";
    private static final String ROUTING_KEY = "appointment.created";

    @Container
    static final GenericContainer<?> rabbitmq = new GenericContainer<>("rabbitmq:3.13-alpine")
            .withExposedPorts(5672)
            .withEnv("RABBITMQ_DEFAULT_USER", USER)
            .withEnv("RABBITMQ_DEFAULT_PASS", PASSWORD)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*\\n", 1));

    @Test
    void rejectedAppointmentIsRoutedToNotificationDlq() throws Exception {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(rabbitmq.getHost());
        connectionFactory.setPort(rabbitmq.getMappedPort(5672));
        connectionFactory.setUsername(USER);
        connectionFactory.setPassword(PASSWORD);

        try (Connection connection = connectionFactory.newConnection(); Channel channel = connection.createChannel()) {
            declareTopology(channel);
            byte[] payload = "{\"eventVersion\":2}".getBytes(StandardCharsets.UTF_8);
            channel.basicPublish(EXCHANGE, ROUTING_KEY,
                    new AMQP.BasicProperties.Builder().messageId("message-id").build(), payload);

            GetResponse delivery = channel.basicGet(QUEUE, false);
            assertThat(delivery).isNotNull();
            channel.basicReject(delivery.getEnvelope().getDeliveryTag(), false);

            GetResponse deadLettered = null;
            for (int attempt = 0; attempt < 20 && deadLettered == null; attempt++) {
                deadLettered = channel.basicGet(DLQ, true);
                if (deadLettered == null) {
                    Thread.sleep(100);
                }
            }

            assertThat(deadLettered).isNotNull();
            assertThat(deadLettered.getBody()).isEqualTo(payload);
        }
    }

    private void declareTopology(Channel channel) throws Exception {
        NotificationRabbitTopologyConfiguration configuration = new NotificationRabbitTopologyConfiguration();
        TopicExchange exchange = configuration.appointmentExchange();
        DirectExchange deadLetterExchange = configuration.appointmentDeadLetterExchange();
        Queue queue = configuration.notificationAppointmentQueue();
        Queue dlq = configuration.notificationAppointmentDlq();
        List<Binding> bindings = List.of(
                configuration.notificationCreatedBinding(exchange, queue),
                configuration.notificationEditedBinding(exchange, queue),
                configuration.notificationCreatedDlqBinding(deadLetterExchange, dlq),
                configuration.notificationEditedDlqBinding(deadLetterExchange, dlq));

        channel.exchangeDeclare(exchange.getName(), exchange.getType(), exchange.isDurable(),
                exchange.isAutoDelete(), exchange.getArguments());
        channel.exchangeDeclare(deadLetterExchange.getName(), deadLetterExchange.getType(),
                deadLetterExchange.isDurable(), deadLetterExchange.isAutoDelete(), deadLetterExchange.getArguments());
        channel.queueDeclare(queue.getName(), queue.isDurable(), queue.isExclusive(), queue.isAutoDelete(), queue.getArguments());
        channel.queueDeclare(dlq.getName(), dlq.isDurable(), dlq.isExclusive(), dlq.isAutoDelete(), dlq.getArguments());
        for (Binding binding : bindings) {
            channel.queueBind(binding.getDestination(), binding.getExchange(), binding.getRoutingKey(), binding.getArguments());
        }
    }
}
