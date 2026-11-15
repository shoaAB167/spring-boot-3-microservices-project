package com.sh_order.microservice.order.client;

import com.sh_order.microservice.order.exception.InventoryServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;

/**
 * Declarative HTTP client for inventory-service (Spring 6 "HTTP interface").
 * Spring generates the implementation at runtime from these annotations; see RestClientConfig.
 *
 * <h2>How the resilience annotations combine</h2>
 * Resilience4j applies its aspects in a fixed order: {@code Retry( CircuitBreaker( call ) )}.
 * <ul>
 *   <li>The circuit breaker sits closest to the call. It records each attempt and, once too
 *       many fail, opens and rejects calls immediately (CallNotPermittedException).</li>
 *   <li>Retry wraps the circuit breaker and repeats failed attempts (see application.properties).</li>
 *   <li>The fallback is on the OUTERMOST aspect (Retry), so it runs only after all retries fail.</li>
 * </ul>
 * The fallback used to be on @CircuitBreaker. Because that is the INNER aspect, it caught the
 * first failure and returned normally, so @Retry never saw an exception and never retried.
 */
public interface InventoryClient {

    // Lombok's @Slf4j can't be used on an interface, so the logger is declared by hand.
    // (The old code imported groovy.util.logging.Slf4j by mistake: a Groovy annotation that
    // does nothing in Java and only compiled because a test library leaked Groovy into main.)
    Logger log = LoggerFactory.getLogger(InventoryClient.class);

    @GetExchange("/api/inventory")
    @CircuitBreaker(name = "inventory")
    @Retry(name = "inventory", fallbackMethod = "fallbackMethod")
    boolean isInStock(@RequestParam String skuCode, @RequestParam Integer quantity);

    /**
     * Runs when inventory-service could not answer (down, timing out, or breaker OPEN).
     * <p>
     * It used to return {@code false}, which told the customer "out of stock" whenever
     * inventory-service was down. "We don't know" is not the same as "no": we fail with a
     * dedicated exception that becomes HTTP 503, and the client can retry later.
     */
    default boolean fallbackMethod(String skuCode, Integer quantity, Throwable throwable) {
        log.warn("Inventory check failed for skuCode {}: {}", skuCode, throwable.toString());
        throw new InventoryServiceUnavailableException(skuCode, throwable);
    }
}
