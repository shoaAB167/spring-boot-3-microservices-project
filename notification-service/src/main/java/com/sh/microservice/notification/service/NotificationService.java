package com.sh.microservice.notification.service;

import com.sh_order.microservice.order.event.OrderPlacedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.stereotype.Service;

/**
 * Consumes OrderPlacedEvent from Kafka and emails the customer.
 *
 * <h2>Failure handling</h2>
 * If sending fails, the exception is NOT caught here. It goes to the container's error handler
 * (config/KafkaConsumerConfig), which retries with a back-off and then moves the message to the
 * dead-letter topic "order-placed-dlt", so nothing is silently lost.
 *
 * <h2>Delivery semantics</h2>
 * Kafka consumers are <b>at-least-once</b>: the offset is committed after this method returns,
 * so a crash right after sending the mail means the event is delivered again and the customer
 * gets a second email. The fix is an idempotent consumer (remember processed order numbers);
 * see docs/ROADMAP.md.
 */
@Slf4j
@Service
public class NotificationService {

    private final JavaMailSender javaMailSender;
    private final String fromAddress;

    public NotificationService(JavaMailSender javaMailSender,
                               @Value("${app.mail.from:springshop@email.com}") String fromAddress) {
        this.javaMailSender = javaMailSender;
        this.fromAddress = fromAddress;
    }

    @KafkaListener(topics = "${app.kafka.topics.order-placed:order-placed}")
    public void listen(OrderPlacedEvent orderPlacedEvent) {
        String orderNumber = orderPlacedEvent.getOrderNumber().toString();
        // Only the order number is logged. The event also holds the customer's email and name
        // (PII), and logs are copied to Loki where many people can read them.
        log.info("Received OrderPlacedEvent for order {}", orderNumber);

        MimeMessagePreparator messagePreparator = mimeMessage -> {
            MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage);
            messageHelper.setFrom(fromAddress);
            messageHelper.setTo(orderPlacedEvent.getEmail().toString());
            messageHelper.setSubject("Your Order with OrderNumber %s is placed successfully".formatted(orderNumber));
            messageHelper.setText("""
                    Hi %s %s,

                    Your order with order number %s is now placed successfully.

                    Best Regards
                    Spring Shop
                    """.formatted(orderPlacedEvent.getFirstName(), orderPlacedEvent.getLastName(), orderNumber));
        };

        // MailException propagates on purpose: see "Failure handling" above.
        javaMailSender.send(messagePreparator);
        log.info("Order confirmation email sent for order {}", orderNumber);
    }
}
