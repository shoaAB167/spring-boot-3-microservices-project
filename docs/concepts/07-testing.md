# 7. Testing

## The idea in plain words

Tests should prove that the code works **against the same kind of infrastructure it runs on**. Mocking a database proves very little: real SQL, real Flyway migrations and real Kafka serialization are where bugs hide. This project's tests start the full Spring Boot app against **real MySQL, MongoDB and Kafka in Docker** (Testcontainers), and replace only what's *outside* the service under test.

| Dependency | Replaced with | Why |
|---|---|---|
| MySQL / MongoDB / Kafka | **Testcontainers** (real servers in Docker) | Same behaviour as production; thrown away after the run |
| inventory-service (when testing order) | **WireMock** (an HTTP stub server) | Test order-service alone; simulate 500s and slow responses on demand |
| Schema Registry | Confluent's **`mock://`** in-memory registry | Real Avro serialization without another container |
| SMTP server | A **Mockito** mock of `JavaMailSender` (`@MockitoBean`) | Check what would be sent, or make sending fail |
| Keycloak (gateway test) | A mock `JwtDecoder` | Test the security *rules* without a real identity provider |

## Where it lives and what each test proves

| Test | Proves |
|---|---|
| [MyOrderServiceApplicationTests](../../my-order-service/src/test/java/com/sh_order/microservice/order/MyOrderServiceApplicationTests.java) | Happy path → 201 **and the Avro event really is on Kafka** with the right key and content · 409 when out of stock · 400 with per-field errors (and inventory never called) · inventory returns 500 → **exactly 3 attempts** (retry works) → 503 |
| [InventoryServiceApplicationTests](../../my-inventory-service/src/test/java/com/sh_inventory/microservice/inventoryservice/InventoryServiceApplicationTests.java) | Flyway seed data is queryable · a new SKU is created, an existing SKU is incremented (no duplicate rows) · mass assignment / invalid input → 400 |
| [MyProductServiceApplicationTests](../../my-product-service/src/test/java/com/sh_product/microservice/MyProductServiceApplicationTests.java) | Create a product in real MongoDB · validation → 400 Problem Details |
| [NotificationServiceApplicationTests](../../notification-service/src/test/java/com/sh/microservice/notification/NotificationServiceApplicationTests.java) | Event → email with the right recipient, subject and greeting · mail failure → retries → **message on `order-placed-dlt`** |
| [ApiGatewayApplicationTests](../../api-gateway/src/test/java/com/sh/api_gateway/ApiGatewayApplicationTests.java) | No token → 401 · downstream down → **POST gets 503** from the fallback · health and Prometheus are public |

Run everything: `mvn verify` from the repo root (Docker must be running).

## Key techniques

- **`@ServiceConnection`** (Spring Boot 3.1+): put it on a container and Boot reads its host and port and overrides `spring.datasource.url`, `spring.kafka.bootstrap-servers`, etc. No `@DynamicPropertySource` boilerplate.
- **Static containers started in a `static {}` block**: one container for the whole test class (fast), shared by all its tests.
- **`@AutoConfigureWireMock(port = 0)`** + `inventory.url=http://localhost:${wiremock.server.port}`: a random port, and the app's client points at the stub.
- **Resetting shared state**: the tests share one Spring context, so the circuit breaker is reset in `@BeforeEach`; otherwise one test's failures could open the breaker for the next test.
- **Asynchronous assertions**: Kafka is async, so tests poll with a deadline (`consumer.poll` in a loop, or Mockito's `verify(mock, timeout(30_000))`). Never use `Thread.sleep`.

## Test pyramid: what's missing

```
        /\        E2E (whole system via the gateway): none, done by hand with Postman
       /  \
      /----\      Integration (one service + real infra): ✔ all 5 services
     /      \
    /--------\    Unit (one class, no Spring): ✘ none yet
```

Next steps (in the [ROADMAP](../ROADMAP.md)): fast unit tests for `OrderService` (Mockito); **consumer-driven contract tests** (Spring Cloud Contract or Pact), so inventory can't break order-service's expectations without a test failing; architecture tests (ArchUnit: "controllers must not use repositories"); coverage reports (JaCoCo) in CI.

## Mistakes we found

[TST-1](../AUDIT.md#5-tests): the order integration test **could never pass**. WireMock wasn't wired to the client URL, `userDetails` was missing, and there was no Kafka. A test that never ran green gives false confidence; CI would have caught it on day one. [TST-2](../AUDIT.md#5-tests): only `contextLoads` in two services. [SEC-9](../AUDIT.md#1-security): a test library in compile scope.

## Interview questions

1. **Unit vs integration vs E2E? How many of each?** Many fast unit tests, fewer integration tests, very few E2E (they're slow and flaky). For microservices, many teams prefer more integration and contract tests per service (the "honeycomb" model).
2. **Why Testcontainers instead of H2?** H2 isn't MySQL: different SQL dialect, locking, types and functions. Flyway MySQL migrations may not even run on it. Testcontainers runs the real engine.
3. **Mock vs stub vs fake?** A stub returns canned answers (WireMock here). A mock also *verifies interactions* (`verify(mailSender).send(...)`). A fake is a lightweight working implementation (the in-memory schema registry).
4. **How do you test something asynchronous like a Kafka consumer?** Produce a real message, then wait with a timeout for the effect (polling or Awaitility), and assert. Never sleep for a fixed time.
5. **What is contract testing and why does it matter here?** Order-service assumes `GET /api/inventory` returns a boolean. If inventory changes that, only a deployed system would show the break. Consumer-driven contracts make the provider's build fail first.
6. **"How did you prove your retry fix works?"** WireMock returns 500 and `verify(3, getRequestedFor(...))` counts the calls: one call before the fix, three after. That's a concrete, checkable claim for an interview.
