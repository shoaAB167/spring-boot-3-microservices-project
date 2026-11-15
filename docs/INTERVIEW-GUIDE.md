# Interview guide: presenting this project

## The 2-minute pitch

> "It's an e-commerce order platform built as five Spring Boot 3 services on Java 21. Every request goes through an API gateway that validates JWTs issued by Keycloak and routes to product, order and inventory services, each with its own database: MongoDB for the catalogue, MySQL for orders and stock, with Flyway migrations.
>
> Placing an order shows both communication styles. The stock check is a **synchronous** REST call, because we need the answer before accepting the order, so it's protected with timeouts, a retry and a Resilience4j circuit breaker. After the order is saved, an **asynchronous** `OrderPlacedEvent` goes to Kafka in Avro format, with the schema enforced by Schema Registry, and the notification service consumes it and emails the customer. Failed messages are retried and then sent to a dead-letter topic.
>
> Everything is observable in Grafana: Prometheus metrics, Loki logs and Tempo traces, linked by trace id, so I can go from a latency spike to the slow trace to its logs. The integration tests run each service against real MySQL, Mongo and Kafka using Testcontainers, with WireMock for the downstream service.
>
> I also audited it myself and found real bugs: for example, the retry never fired because of how Resilience4j orders its aspects. And I know what's next: the outbox pattern for the dual-write problem, and a saga for stock reservation."

Adapt the length: 30 seconds = paragraph 1 + one sentence each from 2 and 3.

## STAR stories (from the audit)

Practise these out loud. Each one shows you can *find*, *explain* and *verify* a problem.

### 1. The retry that never retried ([REL-1](AUDIT.md#2-reliability-and-correctness))
- **Situation:** order-service calls inventory with `@Retry` and `@CircuitBreaker` from Resilience4j.
- **Task:** I wanted to prove the retry worked, so I wrote a test where WireMock returns 500.
- **Action:** WireMock received only **one** call. Resilience4j applies aspects in a fixed order, `Retry(CircuitBreaker(call))`, and the fallback was on the inner circuit breaker. It swallowed the exception and returned `false`, so Retry saw a success. Worse, `false` meant "out of stock", so during an outage customers were told items were unavailable. I moved the fallback to `@Retry`, limited retries to transient errors, skipped retrying when the breaker is open, and made the fallback throw an exception that maps to 503.
- **Result:** the test now asserts exactly 3 calls and a 503. Lesson: *resilience config you haven't tested is configuration you hope works.*

### 2. Inverted timeouts → duplicate orders ([REL-4](AUDIT.md#2-reliability-and-correctness))
- **S:** the gateway had a 3s limit; order-service's worst case calling inventory was about 19s (3s timeouts, 3 attempts, 5s waits).
- **T:** work out what the client experiences when inventory is slow.
- **A:** the gateway returned 503 while order-service kept going and *created the order*. A client retrying after the 503 would place a second order. I built a timeout budget: inner calls 1s × 3 attempts + 300ms waits ≈ 3.6s, gateway 5s.
- **R:** the rule I now apply: *every layer's timeout must exceed the sum of the worst cases below it*, and non-idempotent POSTs need idempotency keys (on the roadmap).

### 3. Mass assignment ([SEC-3](AUDIT.md#1-security))
- **S:** `POST /api/inventory` bound the JSON body straight onto the JPA entity.
- **A:** a caller could send `"id": 1` and overwrite another product's stock, or send negative quantities (OWASP API3). I introduced a request DTO with only `skuCode` and `quantity`, plus Bean Validation, and changed the save to an atomic `UPDATE ... quantity = quantity + ?` to avoid lost updates.
- **R:** tests cover the invalid payload (400) and the increment behaviour.

### 4. Secrets in git ([SEC-1/2](AUDIT.md#1-security))
- **S:** a Docker Hub token and SMTP credentials were committed and pushed.
- **A:** removed them in favour of env vars, documented `.env.example`, git-ignored `.env`, **rotated the credentials**, and planned secret scanning in CI.
- **R:** key point to say: *deleting a secret in a new commit doesn't help, because it's still in history; rotation is the fix.*

### 5. Messages silently dropped ([REL-6](AUDIT.md#2-reliability-and-correctness))
- **A:** Spring Kafka's default error handler retried 10 times with no delay and then dropped the message. I added a fixed back-off, a dead-letter topic, and `ErrorHandlingDeserializer` for poison pills; the test checks that a failing email ends up on `order-placed-dlt`.

## Questions you should expect

Each concept guide ends with fundamentals and scenario questions plus answers. The most likely ones for this project:

| Question | Where the answer is |
|---|---|
| Why microservices here? What are the downsides? | [ARCHITECTURE](ARCHITECTURE.md#why-these-choices) |
| Sync vs async: when do you use each? | [ARCHITECTURE](ARCHITECTURE.md#life-of-one-order) |
| How does the gateway validate a JWT without calling Keycloak? | [02-security](concepts/02-security.md#how-it-works-here) |
| Circuit breaker states and configuration | [03-resilience](concepts/03-resilience.md#circuit-breaker-states) |
| What if Kafka is down when an order is placed? | [05-data](concepts/05-data-and-transactions.md#the-dual-write-problem) (be honest: the event is lost today → outbox) |
| How do you prevent overselling? | [05-data](concepts/05-data-and-transactions.md#distributed-transactions-the-saga-pattern) |
| How do you avoid duplicate emails? | [04-kafka](concepts/04-kafka-and-avro.md#interview-questions) (idempotent consumer) |
| How do you evolve the event schema safely? | [04-kafka](concepts/04-kafka-and-avro.md#avro--schema-registry-what-happens-on-the-wire) |
| How do you debug a slow request across services? | [06-observability](concepts/06-observability.md#interview-questions) |
| How do you test this? | [07-testing](concepts/07-testing.md) |
| What would you improve? | [ROADMAP](ROADMAP.md) |

## Honesty rules

- Say "**I**" for what you built and understand; say "**the next step would be**" for roadmap items.
- If you don't know something, describe how you'd find out (docs, a spike, a test). Interviewers value that more than a guess.
- Be ready to **open the code**: know where `InventoryClient`, `OrderService`, `SecurityConfig`, `Routes` and `KafkaConsumerConfig` are, and what each one does.
