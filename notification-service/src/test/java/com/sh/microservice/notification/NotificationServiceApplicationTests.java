package com.sh.microservice.notification;

import com.sh_order.microservice.order.event.OrderPlacedEvent;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * End-to-end test of the consumer: a real Kafka broker (Testcontainers), Confluent's in-memory
 * "mock://" schema registry, and a Mockito mock in place of the SMTP server.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
		"spring.kafka.properties.schema.registry.url=mock://notification-service-test",
		"app.kafka.retry.interval=100ms",
		"app.kafka.retry.max-retries=2",
		// The mail health check needs a real JavaMailSenderImpl; ours is a mock.
		"management.health.mail.enabled=false"
})
class NotificationServiceApplicationTests {

	@MockitoBean
	private JavaMailSender javaMailSender;

	@Autowired
	private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

	@Autowired
	private ConsumerFactory<String, OrderPlacedEvent> consumerFactory;

	@Test
	void shouldSendConfirmationEmailWhenOrderIsPlaced() throws Exception {
		String orderNumber = UUID.randomUUID().toString();
		kafkaTemplate.send("order-placed", orderNumber, event(orderNumber)).get();

		ArgumentCaptor<MimeMessagePreparator> captor = ArgumentCaptor.forClass(MimeMessagePreparator.class);
		verify(javaMailSender, timeout(30_000)).send(captor.capture());

		// Run the captured preparator against a real MimeMessage to check what would be sent.
		MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
		captor.getValue().prepare(message);
		assertThat(message.getAllRecipients()[0].toString()).isEqualTo("customer@example.com");
		assertThat(message.getSubject()).contains(orderNumber);
		assertThat(message.getContent().toString()).startsWith("Hi Jane Doe,");
	}

	@Test
	void shouldRetryThenPublishToDeadLetterTopicWhenEmailCannotBeSent() throws Exception {
		doThrow(new MailSendException("SMTP server down"))
				.when(javaMailSender).send(any(MimeMessagePreparator.class));

		String orderNumber = UUID.randomUUID().toString();
		kafkaTemplate.send("order-placed", orderNumber, event(orderNumber)).get();

		ConsumerRecord<String, OrderPlacedEvent> deadLetter = pollForKey("order-placed-dlt", orderNumber);
		assertThat(deadLetter.value().getOrderNumber().toString()).isEqualTo(orderNumber);
		assertThat(deadLetter.headers().lastHeader("kafka_dlt-exception-message")).isNotNull();
		verify(javaMailSender, atLeastOnce()).send(any(MimeMessagePreparator.class));
	}

	private static OrderPlacedEvent event(String orderNumber) {
		return new OrderPlacedEvent(orderNumber, "customer@example.com", "Jane", "Doe");
	}

	private ConsumerRecord<String, OrderPlacedEvent> pollForKey(String topic, String key) {
		try (Consumer<String, OrderPlacedEvent> consumer =
					 consumerFactory.createConsumer("dlt-test-" + UUID.randomUUID(), "test")) {
			consumer.subscribe(List.of(topic));
			Instant deadline = Instant.now().plusSeconds(30);
			while (Instant.now().isBefore(deadline)) {
				for (ConsumerRecord<String, OrderPlacedEvent> record : consumer.poll(Duration.ofMillis(500))) {
					if (key.equals(record.key())) {
						return record;
					}
				}
			}
		}
		throw new AssertionError("No record with key " + key + " on " + topic);
	}
}
