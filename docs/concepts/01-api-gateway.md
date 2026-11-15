# 1. API Gateway

## The idea in plain words

Without a gateway, every client must know the address of every service, and every service must handle security, CORS and rate limiting itself. A gateway is a **single entry point** that sits in front of all services and handles these *cross-cutting concerns* in one place. Clients talk to one host (`localhost:9000`); the gateway decides where each request goes.

Typical gateway jobs: **routing**, **authentication**, **CORS**, **rate limiting**, **circuit breaking**, **request/response rewriting**, **API docs aggregation**, and TLS termination.

## Where it lives

| File | What it does |
|---|---|
| [Routes.java](../../api-gateway/src/main/java/com/sh/api_gateway/routes/Routes.java) | The routing table: path → service URL, plus a circuit breaker per route and the fallback |
| [SecurityConfig.java](../../api-gateway/src/main/java/com/sh/api_gateway/config/SecurityConfig.java) | JWT validation, public URLs, CORS |
| [application.properties](../../api-gateway/src/main/resources/application.properties) | Service URLs, Swagger aggregation, Resilience4j defaults, observability |

## How it works here

This project uses **Spring Cloud Gateway Server MVC**: the *servlet* (blocking, thread-per-request) flavour, introduced in Spring Cloud 2023. The original Spring Cloud Gateway is *reactive* (WebFlux/Netty). MVC was chosen because the whole project is servlet-based (Spring MVC, JPA), so there's only one programming model to learn. On Java 21, virtual threads remove most of the scalability argument for reactive.

A route has three parts:

```java
GatewayRouterFunctions.route("order_service")
    .route(RequestPredicates.path("/api/order"),          // 1. predicate: which requests
           HandlerFunctions.http(orderServiceUrl))         // 2. handler: forward to this URL
    .filter(circuitBreaker("orderServiceCircuitBreaker",  // 3. filters: what to do on the way
            URI.create("forward:/fallbackRoute")))
    .build();
```

- **Predicates** can match on path, method, headers, query, host...
- **Filters** run before and/or after forwarding. Here: a circuit breaker on every route, and `setPath("/api-docs")` to rewrite the Swagger URLs.
- **Fallback**: when the circuit breaker trips (downstream down, timed out, or breaker OPEN), the request is *forwarded* inside the gateway to `/fallbackRoute`, which returns 503 with an RFC 9457 Problem Details body.

**Swagger aggregation:** each service serves its own OpenAPI JSON at `/api-docs`. The gateway exposes them as `/aggregate/<service>/v3/api-docs` and lists them in one Swagger UI dropdown (`springdoc.swagger-ui.urls[n]`).

**CORS** (Cross-Origin Resource Sharing): browsers block JavaScript on `site-a.com` from reading responses from `api.site-b.com` unless the API says it's allowed (`Access-Control-Allow-Origin`). Before a non-simple request, the browser sends a *preflight* `OPTIONS` request. CORS is a **browser** protection: it doesn't stop curl or Postman, so it is *not* access control. Allowed origins come from `app.cors.allowed-origins`.

## Mistakes we found

- [REL-3](../AUDIT.md#2-reliability-and-correctness): the fallback route was GET-only, so a failed POST got 404 instead of 503. `forward:` keeps the HTTP method.
- [SEC-5](../AUDIT.md#1-security): two CORS configs, one allowing every origin.
- [BLD-7](../AUDIT.md#4-build-code-quality-and-docs): service URLs hard-coded in Java even though properties existed (one misspelled).
- [REL-4](../AUDIT.md#2-reliability-and-correctness): the gateway's 3s timeout was shorter than the work behind it.

## Interview questions

**Fundamentals**

1. **Why use an API gateway? What are the downsides?**
   One entry point; it centralises auth, CORS, rate limiting and routing; it hides the internal topology; it can aggregate docs. Downsides: an extra network hop (latency), a single point of failure (so run several stateless instances behind a load balancer), and a risk of putting business logic in it. Keep it thin.

2. **API gateway vs load balancer vs service mesh?**
   A load balancer spreads traffic at L4/L7 across instances of *one* service. A gateway works at L7 for *north-south* traffic (client → system): routing by path, auth, API concerns. A service mesh (Istio, Linkerd) handles *east-west* traffic (service → service) with sidecar proxies: mTLS, retries, telemetry, without code changes.

3. **Spring Cloud Gateway reactive vs MVC?**
   Reactive runs on Netty/WebFlux with non-blocking I/O and few threads; it's the mature option with more filters. MVC runs on servlets/Tomcat with thread-per-request; it's simpler to reason about and fits a blocking stack. With Java 21 virtual threads (`spring.threads.virtual.enabled=true`), blocking code scales much better.

4. **What is CORS, and does it protect my API?**
   A browser mechanism that relaxes the same-origin policy for origins the server lists. It does not protect the API from non-browser clients; authentication does that. Never combine `allowedOrigins("*")` with credentials.

5. **What's a BFF (Backend for Frontend)?**
   A gateway per client type (web, mobile), each shaping responses for its client. It's useful when clients need very different data.

**Scenarios**

6. **"The order service is down. What does the client see?"**
   The gateway's circuit breaker records the failure and forwards to `/fallbackRoute`, so the client gets 503 with a Problem Details body. After 5+ calls with ≥50% failures the breaker OPENs, and calls fail instantly without even trying the service (load shedding). After 5s it goes HALF_OPEN and lets 3 trial calls through. Our test `postToDownServiceReturns503FromFallback` proves this works for POST.

7. **"How would you add rate limiting?"**
   Resilience4j `RateLimiter`, or (common in production) Redis-backed token-bucket limiting keyed by client id or IP, so limits are shared across gateway instances. Return `429 Too Many Requests` with `Retry-After`. Not implemented yet; see [ROADMAP](../ROADMAP.md).

8. **"Can someone bypass the gateway?"**
   In this project, yes: services listen on host ports 8080-8083 and don't check tokens. Fixes: network isolation (only the gateway is exposed: Kubernetes `ClusterIP` services plus a `NetworkPolicy`), and/or each service validating the JWT too (defence in depth / zero trust). This is a planned gap.

9. **"Route `/api/order` doesn't match `/api/order/123`. Why?"**
   `path("/api/order")` is an exact match. Use `path("/api/order/**")` once there are sub-resources.
