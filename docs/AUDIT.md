# Project Audit (November 2026)

This is a full review of the codebase: what was wrong, why it mattered, and what was changed. Every fix is covered by a test where a test was possible.

**How to use this file in an interview:** each finding is a ready-made "tell me about a bug you found" story. Pick 2-3 from the *High* rows and practise them with the STAR format in [INTERVIEW-GUIDE.md](INTERVIEW-GUIDE.md).

Severity: **Critical** = exploitable now / data exposure. **High** = wrong behaviour users would hit. **Medium** = breaks under failure or load, or misleads operators. **Low** = hygiene.

---

## Leaked credentials

Two secrets had been committed and pushed to this **public** repository. Removing them from the files does not remove them from git history, so both were treated as public and **rotated by the repository owner on 2026-11-15**.

| # | What | Where it leaked | Status |
|---|---|---|---|
| 1 | Docker Hub personal access token | root `pom.xml`, commit `c4ac514` (2025-07-28) | **Revoked.** The build no longer contains any registry credentials; publish images with `docker login` + `docker push`. |
| 2 | Mailtrap SMTP username/password | `notification-service/.../application.properties`, commit `87c5e47` (2025-03-03) | **Reset.** The new values live only in a local, git-ignored `.env` (template: [.env.example](../.env.example)). |

History was deliberately **not** rewritten: once a credential is revoked, the old value in history is useless, and rewriting would change every commit hash and break existing clones. To stop it happening again: enable GitHub *secret scanning* and *push protection* (repo Settings → Code security), and add `gitleaks` to CI ([ROADMAP](ROADMAP.md) Phase 0).

Also, after pulling these changes:
- Copy `.env.example` to `.env` and fill in `MAIL_USERNAME` / `MAIL_PASSWORD`.
- Run `docker compose down` inside each service folder once, then `docker compose up -d` from the **repo root** (see the README).
- Keycloak now really uses MySQL, so it starts empty and imports `api-gateway/docker/keycloak/realms/realm-export.json`. Copy the new client secret from *Clients → api-gateway-client → Credentials*.
- In IntelliJ, *Maven → Reload project*: the Avro class `OrderPlacedEvent` is now generated into `target/generated-sources/avro` (see BLD-3).

---

## 1. Security

| ID | Sev | Problem | Why it mattered | Fix |
|---|---|---|---|---|
| SEC-1 | Critical | Docker Hub access token hard-coded in the root [pom.xml](../pom.xml). | Anyone reading the public repo could push images to your Docker Hub account: a **supply-chain attack** risk for anyone who pulls them. | Token removed and revoked. The root POM is now a plain aggregator (see BLD-1). |
| SEC-2 | Critical | Mailtrap SMTP credentials committed in notification-service `application.properties`. | Anyone could send mail through your account. | Read from `${MAIL_USERNAME}` / `${MAIL_PASSWORD}`, supplied by a git-ignored `.env` (loaded via `spring.config.import`) or real env vars. Rotated. |
| SEC-3 | High | `POST /api/inventory` accepted the **JPA entity** `Inventory` as the request body. | **Mass assignment** (OWASP API3:2023). A caller could send `"id": 1` to overwrite any existing row, or send a negative quantity. | New [InventoryRequest](../my-inventory-service/src/main/java/com/sh_inventory/microservice/inventoryservice/dto/InventoryRequest.java) DTO containing only `skuCode` and `quantity`, with validation. Test: `shouldRejectInvalidInventoryRequest`. |
| SEC-4 | Medium | No input validation in any service. | Garbage stored in the DB; `POST /api/order` without `userDetails` passed the stock check, then crashed with a NullPointerException → 500. | Bean Validation (`@Valid`, `@NotBlank`, `@Positive`, `@Email`) on every request DTO; 400 responses list every invalid field. Tests in each service. |
| SEC-5 | Medium | Two CORS configurations: a `WebMvcConfigurer` allowing **every origin**, and a Security one with `allowedOrigins("*")`. | Any website could call the API from a user's browser, and with two configs it was unclear which one applied. | One CORS config in [SecurityConfig](../api-gateway/src/main/java/com/sh/api_gateway/config/SecurityConfig.java) with explicit origins from `app.cors.allowed-origins`. `CorsConfig.java` deleted. |
| SEC-6 | Medium | Customer email, first and last name logged at INFO (`log.info("... {}", orderPlacedEvent)`) in order and notification services. | Logs are copied to Loki and kept for a long time. PII in logs is a GDPR problem and a common audit finding. | Only the order number is logged. |
| SEC-7 | Low | Public-endpoint pattern `"*/actuator/prometheus"` (missing leading `/`) never matched; `/actuator/health` needed a token although the README said to open it in a browser. | Prometheus got 401 from the gateway, so there were no gateway metrics (see OBS-1). | Patterns fixed. Health is public but shows details (circuit-breaker states) only with a valid token (`show-details=when-authorized`). Test: `healthAndPrometheusArePublic`. |
| SEC-8 | Low | Unpinned images: `grafana/loki:main` (a development build!), `kafka-ui:latest`, `mysql:latest`, `kafka-native:latest`. | Builds aren't reproducible, and you run whatever the publisher pushed most recently (supply chain). | All images pinned to versions. |
| SEC-9 | Low | `rest-assured` (a test library) had **compile** scope, so it and its Groovy dependency shipped inside the production jars. | Bigger attack surface; it also hid the bug in CODE-1. | Changed to `<scope>test</scope>` in all services. |

**Not fixed yet (planned, see [ROADMAP.md](ROADMAP.md)):** the services behind the gateway have no authentication of their own, so anyone who can reach ports 8080-8083 bypasses the gateway. There's no authorization either: any valid token can add inventory. The JWT `aud` claim isn't checked. The order price is supplied by the client.

---

## 2. Reliability and correctness

| ID | Sev | Problem | Why it mattered | Fix |
|---|---|---|---|---|
| REL-1 | High | `@Retry` on the inventory client **never retried**. The `fallbackMethod` sat on `@CircuitBreaker`. Resilience4j nests aspects as `Retry( CircuitBreaker( call ) )`, so the inner circuit breaker caught the first error, returned the fallback value, and Retry saw a success. | The retry configuration existed but never ran, so one network blip failed the order. | Fallback moved to `@Retry` (the outer aspect); only retryable errors (I/O, 5xx) are retried; `CallNotPermittedException` (breaker open) isn't retried. See [InventoryClient](../my-order-service/src/main/java/com/sh_order/microservice/order/client/InventoryClient.java). Test `shouldRetryThenReturn503WhenInventoryServiceFails` checks with WireMock that inventory receives **exactly 3** calls. |
| REL-2 | High | When inventory-service was down, the fallback returned `false`, so the customer was told **"out of stock"**, and then `RuntimeException` turned that into **500**. | Lying to users about stock during an outage loses sales, and 500 hides the real cause. | Fallback throws `InventoryServiceUnavailableException`, which becomes **503**; out-of-stock becomes **409**; all errors use RFC 9457 Problem Details via [GlobalExceptionHandler](../my-order-service/src/main/java/com/sh_order/microservice/order/exception/GlobalExceptionHandler.java). |
| REL-3 | High | The gateway's `/fallbackRoute` only accepted **GET**. The circuit breaker *forwards* failed requests and keeps the HTTP method, so a failed `POST /api/order` matched nothing. | Clients got 404 for an outage instead of 503, so they couldn't tell "wrong URL" from "try again later". | The fallback route accepts every method and returns a Problem Details 503. Test: `postToDownServiceReturns503FromFallback`. |
| REL-4 | Medium | **Inverted timeout budget.** Gateway timeout was 3s, but order-service could spend up to ~19s calling inventory (3s read timeout × 3 attempts + 2 × 5s waits). | The gateway returned 503 while order-service was **still creating the order**. A client that retries after a 503 creates a **duplicate order**. | Order: 1s timeouts, 300ms between retries (worst case ~3.6s). Gateway: 5s. Rule: *each layer's timeout must be longer than everything below it.* |
| REL-5 | Medium | `@Transactional` on the whole `OrderService` wrapped the **remote HTTP call** inside a DB transaction; the Kafka send result was never checked; `send()` could block for 60s if Kafka was down. | A DB connection was held during network waits (pool exhaustion under load), and Kafka failures were silent. | Remote call happens first, outside any transaction; only `save()` is transactional; the send has a `whenComplete` callback that logs failures; `max.block.ms=3000`. The remaining dual-write gap is documented (outbox, see ROADMAP). |
| REL-6 | Medium | Notification consumer: on mail failure Spring Kafka's default retried **10 times with no delay, then dropped the message**. A message that couldn't be deserialized could block the partition. | Customers silently never got their email; one bad message could stop all notifications. | `ErrorHandlingDeserializer` + `DefaultErrorHandler` (fixed back-off) + **dead-letter topic** `order-placed-dlt`. See [KafkaConsumerConfig](../notification-service/src/main/java/com/sh/microservice/notification/config/KafkaConsumerConfig.java). Test `shouldRetryThenPublishToDeadLetterTopicWhenEmailCannotBeSent`. |
| REL-7 | Medium | Keycloak used the **old WildFly variable names** (`DB_VENDOR`, `DB_ADDR`...), which Keycloak 17+ ignores. It silently used its embedded H2 database inside the container, so the MySQL container was never used. `realm-export.json` was **empty**. | Every time the container was recreated, the realm and clients were lost and had to be set up by hand. | `KC_DB*` variables, MySQL health check, and a real realm file that is imported on start. |
| REL-8 | Low | Kafka UI used `KAFKA_CLUSTERS_NAME` (the correct names are `KAFKA_CLUSTERS_0_*`). | The cluster wasn't configured, which is why the old README said to "create Kafka cluster" by hand. | Variable names fixed. |
| REL-9 | Low | Adding stock for an existing SKU inserted a **new row** each time; the stock check looks at one row at a time. | 60 + 60 units in two rows made a request for 100 units say "not in stock". | Atomic `UPDATE ... SET quantity = quantity + ?`, inserting only if the SKU is new (also avoids a lost-update race). Test `shouldCreateNewSkuThenIncrementExistingSku`. |
| API-1 | Medium | `POST /api/order` returned the plain text `"Order Placed Successfully"`; `OrderResponse.java` was an empty file. | The client never got the **order number**, so it couldn't track or reference the order. | Returns JSON `{orderNumber, skuCode, totalPrice, quantity}`. |

---

## 3. Observability

| ID | Sev | Problem | Fix |
|---|---|---|---|
| OBS-1 | Medium | Prometheus scraped the gateway at `host.docker.internal:9090`, which is **Prometheus itself**; the gateway runs on 9000. Combined with SEC-7, there were zero gateway metrics. | Target `:9000`; `extra_hosts` so `host.docker.internal` also works on Linux. |
| OBS-2 | Medium | The gateway tagged its metrics and traces as **`product-service`** (copy-paste). It also had `management.tracing.service-name` (not a Spring Boot property) and an OTLP endpoint with no OTLP exporter on the classpath and no OTLP receiver in Tempo. | Tag fixed to `api-gateway`; dead settings removed; Zipkin endpoint set explicitly. |
| OBS-3 | Low | Clicking from a log line in Grafana to its trace didn't work. Spring Boot 3.2+ logs `[traceId-spanId]`, but the Loki regex expected `[app,traceId,spanId]`. It was also greedy, so a log message containing commas captured the wrong text. | `logging.pattern.correlation=[app,traceId,spanId]` in every service; strict hex regex in [datasources.yml](../api-gateway/docker/grafana/datasources.yml). |
| OBS-4 | Low | Inventory's `spring.application.name` was `Inventory Service` (with a space); names were inconsistent (`my-order-service` vs the `order-service` tag). | Consistent names: `api-gateway`, `product-service`, `order-service`, `inventory-service`, `notification-service`. |
| OBS-5 | Low | Traces stopped at Kafka: the order trace and the notification trace weren't connected. | Observation enabled on `KafkaTemplate` and the listener, so the `traceparent` header travels inside the Kafka record. |

---

## 4. Build, code quality and docs

| ID | Sev | Problem | Fix |
|---|---|---|---|
| BLD-1 | Medium | Root POM used Boot **3.2.4** / Cloud **2023.0.1** while every module used **3.4.2** / **2024.0.0**. The modules don't inherit from the root POM, so its image-publishing config (and the leaked token) never even applied. | Root POM is now a plain aggregator: `mvn verify` at the root builds all five services. |
| BLD-2 | Low | Duplicate or redundant dependencies (actuator declared twice; `gateway-server-mvc` next to its starter; springdoc `-api` next to `-ui`). | Removed. |
| BLD-3 | Medium | Avro-**generated** `OrderPlacedEvent.java` (560 lines) was committed to `src/main/java` in **two** services. | Generated at build time into `target/generated-sources/avro`; the committed copies were deleted. The `.avsc` schema is the only source of truth. |
| BLD-4 | Medium | Order-service: Flyway disabled + `ddl-auto=update` (Hibernate silently altering the production schema). Its `V1__init.sql` started with `--we are...`, which is **not a comment in MySQL** (it needs `-- ` with a space) and would have failed. | Flyway on, `baseline-on-migrate` for the existing DB, `ddl-auto=validate`, `open-in-view=false`, V1 fixed. |
| BLD-5 | Low | Tempo's runtime trace data (30 binary files) was committed and changed on every run. | Added to `.gitignore` and removed from the index (the files stay on disk). |
| BLD-6 | Low | README saved as **UTF-16**, so git treated it as binary (no diffs) and GitHub rendering suffered. Image links used Windows absolute paths (`\spring-boot-3...\Project Images\...`), which don't work on GitHub. Steps were stale, and the sample payload had your personal email. | README rewritten in UTF-8 with relative image links, a single-command setup, an API table, and `customer@example.com` in the sample. |
| BLD-7 | Low | Gateway routes hard-coded `http://localhost:8080` even though properties existed for it (one was misspelled: `order.service.utl`). The datasources used `host.docker.internal`, which only resolves on Docker Desktop. | Routes read `product/order/inventory.service.url` (overridable with env vars); datasources use `localhost`. |
| BLD-8 | Low | Deprecated APIs and config: `ClientHttpRequestFactories` (deprecated in Boot 3.4), `MySQL8Dialect`, `version:` in compose files. Dead config: a Resilience4j `timelimiter` in order-service (it only applies to async methods), and gateway `retry` settings with no retry filter. | Replaced with current APIs (`ClientHttpRequestFactoryBuilder`) or removed. |
| BLD-9 | Low | Three separate `docker compose up` commands in three folders were needed. | Root [docker-compose.yml](../docker-compose.yml) uses `include:`, so one `docker compose up -d` starts everything. |
| CODE-1 | Low | `import groovy.util.logging.Slf4j` in `OrderService` and `InventoryClient`: a **Groovy** annotation, which does nothing in Java. It only compiled because of SEC-9. | Lombok `@Slf4j` (or an explicit logger in the interface). |

---

## 5. Tests

| ID | Sev | Problem | Fix |
|---|---|---|---|
| TST-1 | High | The order-service integration test **could not pass**: WireMock was never wired to `inventory.url` (the call went to the real port 8082 → fallback → 500), the payload had no `userDetails` (NPE), and there was no Kafka or Schema Registry. | Test properties point `inventory.url` at WireMock; Kafka runs in Testcontainers; `mock://` Schema Registry; the circuit breaker is reset between tests. |
| TST-2 | Medium | The gateway and notification tests only checked `contextLoads`; no failure path was tested anywhere. | New tests (all passing): order happy path including **reading the event back from Kafka**, 400, 409, 503 with retry count; gateway 401, POST fallback 503, public actuator; notification email content and **dead-letter topic**; inventory upsert and validation; product validation. |

Run everything with `mvn verify` from the repo root (Docker must be running).
