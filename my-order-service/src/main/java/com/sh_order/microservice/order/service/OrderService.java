package com.sh_order.microservice.order.service;

import com.sh_order.microservice.order.client.InventoryClient;
import com.sh_order.microservice.order.dto.OrderRequest;
import com.sh_order.microservice.order.dto.OrderResponse;
import com.sh_order.microservice.order.event.OrderPlacedEvent;
import com.sh_order.microservice.order.exception.ProductOutOfStockException;
import com.sh_order.microservice.order.model.Order;
import com.sh_order.microservice.order.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Places an order in three steps:
 * <ol>
 *   <li><b>Synchronous</b> stock check against inventory-service (REST). We need the answer
 *       before we can accept the order.</li>
 *   <li>Save the order in this service's own MySQL database.</li>
 *   <li><b>Asynchronous</b> OrderPlacedEvent to Kafka. notification-service reacts to it in its
 *       own time, so a slow mail server cannot slow down or fail order placement.</li>
 * </ol>
 *
 * <h2>Why there is no class-level @Transactional any more</h2>
 * Previously the whole method, including the HTTP call, ran inside one database transaction.
 * That held a pooled DB connection open while waiting on the network (up to several seconds
 * with retries), and rolling back the database cannot undo a remote call or a Kafka message
 * anyway. Now the remote call happens first, outside any transaction, and only
 * {@code orderRepository.save()} is transactional (Spring Data repositories are by default).
 *
 * <h2>Known gap: the dual-write problem</h2>
 * Steps 2 and 3 write to two systems (MySQL and Kafka) that do not share a transaction. If the
 * process crashes between them, or Kafka is down, the order exists but no event is ever sent
 * and the customer gets no email. The production fix is the Transactional Outbox pattern.
 * See docs/ROADMAP.md and docs/concepts/05-data-and-transactions.md.
 */
@Slf4j
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;
    private final String orderPlacedTopic;

    public OrderService(OrderRepository orderRepository,
                        InventoryClient inventoryClient,
                        KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate,
                        @Value("${app.kafka.topics.order-placed:order-placed}") String orderPlacedTopic) {
        this.orderRepository = orderRepository;
        this.inventoryClient = inventoryClient;
        this.kafkaTemplate = kafkaTemplate;
        this.orderPlacedTopic = orderPlacedTopic;
    }

    public OrderResponse placeOrder(OrderRequest orderRequest) {
        // 1. Remote call, deliberately outside any DB transaction.
        boolean inStock = inventoryClient.isInStock(orderRequest.skuCode(), orderRequest.quantity());
        if (!inStock) {
            throw new ProductOutOfStockException(orderRequest.skuCode(), orderRequest.quantity());
        }
        // KNOWN GAP: this is "check-then-act". Two customers can both see the last unit as in
        // stock and both orders succeed, because stock is never reserved or decremented.
        // See the Saga section in docs/ROADMAP.md.

        // 2. Short local transaction.
        Order order = orderRepository.save(mapToOrder(orderRequest));

        // 3. Fire-and-forget event (with a callback so failures are at least visible).
        publishOrderPlacedEvent(order, orderRequest.userDetails());

        return new OrderResponse(order.getOrderNumber(), order.getSkuCode(), order.getPrice(), order.getQuantity());
    }

    private void publishOrderPlacedEvent(Order order, OrderRequest.UserDetails user) {
        OrderPlacedEvent event = new OrderPlacedEvent(
                order.getOrderNumber(), user.email(), user.firstName(), user.lastName());

        // The order number is the message KEY. Kafka hashes the key to pick a partition, so all
        // events for one order land on the same partition and stay in order.
        kafkaTemplate.send(orderPlacedTopic, order.getOrderNumber(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        // Not swallowed silently any more. Still lost, though: hence the outbox gap above.
                        log.error("Failed to publish OrderPlacedEvent for order {}", order.getOrderNumber(), ex);
                    } else {
                        // Log the order number only: the event contains the customer's email
                        // and name (PII), which must not be copied into log storage.
                        log.info("Published OrderPlacedEvent for order {} to {}-{}@{}",
                                order.getOrderNumber(),
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    private static Order mapToOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        order.setPrice(orderRequest.price().multiply(BigDecimal.valueOf(orderRequest.quantity())));
        order.setQuantity(orderRequest.quantity());
        order.setSkuCode(orderRequest.skuCode());
        return order;
    }
}
