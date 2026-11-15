# Documentation

These docs explain **what** this project does, **why** it is built this way, and **how to talk about it** in an interview.

## Suggested reading order

1. [ARCHITECTURE.md](ARCHITECTURE.md): the big picture and the life of one order, step by step.
2. The concept guides, in this order (each one builds on the previous):

   | # | Guide | You'll be able to explain |
   |---|---|---|
   | 1 | [API Gateway](concepts/01-api-gateway.md) | Why a single entry point, routing, filters, Swagger aggregation, CORS |
   | 2 | [Security: OAuth2, JWT, Keycloak](concepts/02-security.md) | Resource server, JWT validation, client credentials, OWASP API issues |
   | 3 | [Resilience](concepts/03-resilience.md) | Circuit breaker states, retry, timeouts and timeout budgets, fallbacks |
   | 4 | [Kafka, Avro, Schema Registry](concepts/04-kafka-and-avro.md) | Async messaging, partitions, consumer groups, delivery guarantees, DLT, schema evolution |
   | 5 | [Data and transactions](concepts/05-data-and-transactions.md) | Database per service, Flyway, transaction boundaries, the dual-write problem, outbox, saga |
   | 6 | [Observability](concepts/06-observability.md) | Metrics, logs, traces, Micrometer, Prometheus, Loki, Tempo, Grafana, correlation |
   | 7 | [Testing](concepts/07-testing.md) | Testcontainers, WireMock, the test pyramid, what each test proves |

3. [AUDIT.md](AUDIT.md): every problem found in the code review and how it was fixed. These are your "tell me about a bug you fixed" stories.
4. [ROADMAP.md](ROADMAP.md): what is still missing. Interviewers often ask *"what would you improve?"*, and this is your answer.
5. [INTERVIEW-GUIDE.md](INTERVIEW-GUIDE.md): the 2-minute pitch, STAR stories, and the questions to expect.

## How each concept guide is structured

- **The idea in plain words**: what problem it solves.
- **Where it lives in this repo**: file links, so you can show the code.
- **How it works here**: the real flow, with the real config values.
- **Mistakes we found**: links to audit items. Real bugs make the best interview answers.
- **Interview questions**: fundamentals first, then scenario questions ("what happens if...").

## One rule for interviews

Only claim what this repo actually does. When asked about something listed in [ROADMAP.md](ROADMAP.md), say *"that's the next step; here is how I would do it"*. That shows you understand it, and it is honest.
