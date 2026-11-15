package com.sh_order.microservice.order.exception;

/**
 * The requested quantity is not available. This is a business rule failing, not a server
 * error, so it is mapped to HTTP 409 Conflict instead of the generic 500 it used to produce.
 */
public class ProductOutOfStockException extends RuntimeException {

    public ProductOutOfStockException(String skuCode, Integer quantity) {
        super("Product with skuCode " + skuCode + " is not in stock for quantity " + quantity);
    }
}
