package com.sh_order.microservice.order.exception;

/**
 * inventory-service could not be reached (down, too slow, or circuit breaker OPEN).
 * Mapped to HTTP 503 Service Unavailable by {@link GlobalExceptionHandler}.
 */
public class InventoryServiceUnavailableException extends RuntimeException {

    public InventoryServiceUnavailableException(String skuCode, Throwable cause) {
        super("Could not check stock for skuCode " + skuCode + ", inventory service is unavailable", cause);
    }
}
