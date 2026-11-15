# Roadmap: known gaps and next steps

The [audit](AUDIT.md) fixed the bugs and misconfigurations. What's left below are **design gaps**: each one is a real piece of work, and each is a good answer to *"what would you improve?"* in an interview. They're grouped by phase; finish one phase before starting the next.

Status: ✅ done · 🟡 partly done · ⬜ not started

## Phase 0: Secrets hygiene 🟡

- ✅ Secrets removed from the code (Docker token, Mailtrap); `.env.example` documents every setting.
- ✅ Docker Hub token revoked and Mailtrap credentials reset (2026-11-15; see [AUDIT.md](AUDIT.md#leaked-credentials)).
- ✅ Local secrets in a git-ignored `.env`, loaded by every service via `spring.config.import`.
- ⬜ Secret scanning so it can't happen again: enable GitHub push protection, and add `gitleaks` to CI.

## Phase 1: API security (OWASP API Top 10) 🟡

- ✅ Mass assignment fixed, input validation everywhere, CORS locked down, PII removed from logs.
- ⬜ **Zero trust between services.** Make product, order and inventory OAuth2 resource servers too (forward the user's token, or use client credentials for service-to-service), so that reaching port 8081 directly doesn't bypass security.
- ⬜ **Authorization.** Keycloak roles → `hasRole("inventory-admin")` for `POST /api/inventory`; validate the `aud` claim.
- ⬜ **Don't trust the client's price.** Order-service should get the price from product-service. That first needs a `skuCode` on `Product`: products and inventory aren't linked by any field today.
- ⬜ Rate limiting at the gateway (429 + `Retry-After`), and pagination on `GET /api/product`.
- ⬜ An idempotency key on `POST /api/order` (the client sends `Idempotency-Key`; the server returns the stored result for a repeat).

## Phase 2: CI/CD and supply chain ⬜

- ⬜ GitHub Actions: `mvn verify` on every push and PR (Docker is available on `ubuntu-latest` runners, so Testcontainers works).
- ⬜ Dependency and image scanning (OWASP Dependency-Check or Trivy, Dependabot), SBOM generation (CycloneDX).
- ⬜ Build images with `mvn spring-boot:build-image` (Cloud Native Buildpacks), tag them with the git SHA, and push using credentials stored as CI secrets.
- ⬜ A parent POM / BOM to share dependency versions (springdoc, loki4j, Confluent) across services.

## Phase 3: Data consistency ⬜

- ⬜ **Transactional outbox** for `OrderPlacedEvent` (fixes the dual-write gap; see [05-data-and-transactions](concepts/05-data-and-transactions.md#the-dual-write-problem)).
- ⬜ **Stock reservation saga**: inventory reserves stock atomically (`quantity >= :q` in the `WHERE` clause); the order is PENDING → CONFIRMED / CANCELLED with compensation. This fixes overselling.
- ⬜ **Idempotent consumer** in notification-service (dedupe on `orderNumber`), so redelivery never sends two emails.
- ⬜ Unique constraint on `t_inventory.sku_code` (a migration that merges any existing duplicates first).
- ⬜ A shared schema module, or the Schema Registry Maven plugin, so the `.avsc` file isn't copied into two services; a compatibility check in CI.

## Phase 4: Testing ⬜

- ⬜ Unit tests for the service classes (Mockito), plus JaCoCo coverage.
- ⬜ Consumer-driven contract tests between order and inventory (Spring Cloud Contract / Pact).
- ⬜ ArchUnit rules for the layering.

## Phase 5: Containers, Kubernetes, infrastructure as code ⬜

- ⬜ Run the services themselves in Docker Compose (today only the infrastructure runs in Docker).
- ⬜ Kubernetes manifests or a Helm chart: Deployments, Services (ClusterIP for everything except the gateway), ConfigMaps/Secrets, liveness/readiness probes (`/actuator/health/liveness`, `/readiness`), resource limits, HPA, NetworkPolicies.
- ⬜ Kafka in KRaft mode (no ZooKeeper; ZooKeeper support was removed in Kafka 4.0).
- ⬜ Terraform for a cloud environment (optional).

> **Resume check:** if your resume mentions **Kubernetes** or **CI/CD**, this repo doesn't show them yet. Either build Phase 2/5 before interviews, or describe them as "planned, here's how I'd do it". Never claim more than the repo shows: interviewers do open the link.

## Phase 6: Observability (polish) 🟡

- ✅ Metrics/logs/traces wired up and correlated; traces cross Kafka.
- ⬜ Move from Brave/Zipkin to OpenTelemetry (OTLP) export; Grafana dashboards as code (provisioned JSON); alert rules (error rate, latency SLOs, circuit breaker open, Kafka consumer lag).
- ⬜ JSON structured logging (Spring Boot 3.4 supports `logging.structured.format.console=ecs`).
