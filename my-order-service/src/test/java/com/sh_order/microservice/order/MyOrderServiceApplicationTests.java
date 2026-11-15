package com.sh_order.microservice.order;

import com.sh_order.microservice.order.event.OrderPlacedEvent;
import com.sh_order.microservice.order.stub.InventoryStubs;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.restassured.RestAssured;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.resetAllRequests;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Integration tests: the real Spring Boot app runs against a real MySQL and a real Kafka
 * broker (Testcontainers starts both in Docker), with inventory-service replaced by WireMock.
 * <p>
 * Schema Registry is replaced by Confluent's in-memory registry: any schema.registry.url
 * starting with "mock://" uses it, so no Schema Registry container is needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "inventory.url=http://localhost:${wiremock.server.port}",
        "spring.kafka.producer.properties.schema.registry.url=" + MyOrderServiceApplicationTests.MOCK_SCHEMA_REGISTRY,
        "resilience4j.retry.instances.inventory.wait-duration=10ms"
})
@AutoConfigureWireMock(port = 0)
class MyOrderServiceApplicationTests {

    static final String MOCK_SCHEMA_REGISTRY = "mock://order-service-test";

    @ServiceConnection
    static MySQLContainer<?> mySQLContainer = new MySQLContainer<>("mysql:8.3.0");

    @ServiceConnection
    static KafkaContainer kafkaContainer = new KafkaContainer("apache/kafka-native:3.8.0");

    static {
        mySQLContainer.start();
        kafkaContainer.start();
    }

    @LocalServerPort
    private Integer port;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setup() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        resetAllRequests();
        // Tests share one application context, so start each test with a CLOSED breaker.
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @Test
    void shouldPlaceOrderAndPublishOrderPlacedEvent() {
        InventoryStubs.stubInventoryCall("iphone_15", 2, true);

        String orderNumber = RestAssured.given()
                .contentType("application/json")
                .body(orderJson("iphone_15", 2))
                .when()
                .post("/api/order")
                .then()
                .log().ifValidationFails()
                .statusCode(201)
                .body("orderNumber", notNullValue())
                .body("skuCode", equalTo("iphone_15"))
                .body("quantity", equalTo(2))
                .body("totalPrice", equalTo(2000)) // unit price 1000 x quantity 2
                .extract().path("orderNumber");

        // The event must reach Kafka, keyed by order number, with the customer's details.
        OrderPlacedEvent event = consumeOrderPlacedEvent(orderNumber);
        assertThat(event.getEmail().toString()).isEqualTo("customer@example.com");
        assertThat(event.getFirstName().toString()).isEqualTo("Jane");
    }

    @Test
    void shouldReturn409WhenProductIsOutOfStock() {
        InventoryStubs.stubInventoryCall("iphone_15", 1000, false);

        RestAssured.given()
                .contentType("application/json")
                .body(orderJson("iphone_15", 1000))
                .when()
                .post("/api/order")
                .then()
                .statusCode(409)
                .contentType("application/problem+json")
                .body("title", equalTo("Product out of stock"));
    }

    @Test
    void shouldReturn400WithFieldErrorsForInvalidRequest() {
        RestAssured.given()
                .contentType("application/json")
                .body("""
                        { "skuCode": "", "price": -1, "quantity": 0 }
                        """)
                .when()
                .post("/api/order")
                .then()
                .statusCode(400)
                .body("errors.skuCode", notNullValue())
                .body("errors.price", notNullValue())
                .body("errors.quantity", notNullValue())
                .body("errors.userDetails", notNullValue());

        // Validation runs before any business logic: inventory-service was never called.
        verify(0, getRequestedFor(urlPathEqualTo("/api/inventory")));
    }

    @Test
    void shouldRetryThenReturn503WhenInventoryServiceFails() {
        InventoryStubs.stubInventoryFailure();

        RestAssured.given()
                .contentType("application/json")
                .body(orderJson("iphone_15", 1))
                .when()
                .post("/api/order")
                .then()
                .statusCode(503)
                .body("title", equalTo("Inventory service unavailable"));

        // max-attempts=3, so we expect exactly 3 calls. Before the fix this was 1: the
        // circuit breaker's fallback swallowed the error, so @Retry never retried.
        verify(3, getRequestedFor(urlPathEqualTo("/api/inventory")));
    }

    private static String orderJson(String skuCode, int quantity) {
        return """
                {
                  "skuCode": "%s",
                  "price": 1000,
                  "quantity": %d,
                  "userDetails": { "email": "customer@example.com", "firstName": "Jane", "lastName": "Doe" }
                }
                """.formatted(skuCode, quantity);
    }

    /** Reads the order-placed topic until it finds the event for this order (or times out). */
    private static OrderPlacedEvent consumeOrderPlacedEvent(String orderNumber) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "order-service-test",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class,
                "schema.registry.url", MOCK_SCHEMA_REGISTRY,
                "specific.avro.reader", true);

        try (KafkaConsumer<String, OrderPlacedEvent> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("order-placed"));
            Instant deadline = Instant.now().plusSeconds(30);
            while (Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, OrderPlacedEvent> record : consumer.poll(Duration.ofMillis(500))) {
                    if (orderNumber.equals(record.key())) {
                        return record.value();
                    }
                }
            }
        }
        throw new AssertionError("No OrderPlacedEvent with key " + orderNumber + " was published");
    }
}
