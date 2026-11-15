package com.sh.api_gateway.routes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;

import static org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.setPath;

/**
 * Gateway routing table (Spring Cloud Gateway "MVC" flavour: servlet based, not reactive).
 *
 * Every route has three parts:
 *   1. a predicate  - which incoming requests it matches (the path),
 *   2. a handler    - where to forward them (the downstream service URL),
 *   3. filters      - what to do on the way (circuit breaker, path rewrite...).
 *
 * Downstream URLs come from application.properties so they can change per environment
 * (localhost for dev, a Kubernetes service name in a cluster) without recompiling.
 */
@Configuration(proxyBeanMethods = false)
public class Routes {

    private static final URI FALLBACK = URI.create("forward:/fallbackRoute");

    @Value("${product.service.url}")
    private String productServiceUrl;

    @Value("${order.service.url}")
    private String orderServiceUrl;

    @Value("${inventory.service.url}")
    private String inventoryServiceUrl;

    @Bean
    public RouterFunction<ServerResponse> productServiceRoute() {
        return GatewayRouterFunctions.route("product_service")
                .route(RequestPredicates.path("/api/product"), HandlerFunctions.http(productServiceUrl))
                .filter(circuitBreaker("productServiceCircuitBreaker", FALLBACK))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> productServiceSwaggerRoute() {
        return GatewayRouterFunctions.route("product_service_swagger")
                .route(RequestPredicates.path("/aggregate/product-service/v3/api-docs"), HandlerFunctions.http(productServiceUrl))
                .filter(circuitBreaker("productServiceSwaggerCircuitBreaker", FALLBACK))
                .filter(setPath("/api-docs"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> orderServiceRoute() {
        return GatewayRouterFunctions.route("order_service")
                .route(RequestPredicates.path("/api/order"), HandlerFunctions.http(orderServiceUrl))
                .filter(circuitBreaker("orderServiceCircuitBreaker", FALLBACK))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> orderServiceSwaggerRoute() {
        return GatewayRouterFunctions.route("order_service_swagger")
                .route(RequestPredicates.path("/aggregate/order-service/v3/api-docs"), HandlerFunctions.http(orderServiceUrl))
                .filter(circuitBreaker("orderServiceSwaggerCircuitBreaker", FALLBACK))
                .filter(setPath("/api-docs"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> inventoryServiceRoute() {
        return GatewayRouterFunctions.route("inventory_service")
                .route(RequestPredicates.path("/api/inventory"), HandlerFunctions.http(inventoryServiceUrl))
                .filter(circuitBreaker("inventoryServiceCircuitBreaker", FALLBACK))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> inventoryServiceSwaggerRoute() {
        return GatewayRouterFunctions.route("inventory_service_swagger")
                .route(RequestPredicates.path("/aggregate/inventory-service/v3/api-docs"), HandlerFunctions.http(inventoryServiceUrl))
                .filter(circuitBreaker("inventoryServiceSwaggerCircuitBreaker", FALLBACK))
                .filter(setPath("/api-docs"))
                .build();
    }

    /**
     * Where the circuit breaker sends a request when the downstream service is down,
     * too slow, or the breaker is OPEN.
     *
     * It must accept EVERY HTTP method: "forward:" keeps the original method, so a failed
     * POST /api/order arrives here as a POST. The earlier version only mapped GET, so a
     * failed POST ended in a confusing 404/405 instead of a 503.
     */
    @Bean
    public RouterFunction<ServerResponse> fallbackRoute() {
        return GatewayRouterFunctions.route("fallbackRoute")
                .route(RequestPredicates.path("/fallbackRoute"), request -> {
                    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                            HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable, please try again later");
                    problem.setTitle("Service Unavailable");
                    return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
                })
                .build();
    }
}
