package com.sh_order.microservice.order.dto;

import java.math.BigDecimal;

/**
 * Body of the 201 response to POST /api/order.
 * The endpoint used to return the plain text "Order Placed Successfully", so the caller never
 * learned the order number it needs to track the order or contact support.
 *
 * @param totalPrice unit price x quantity
 */
public record OrderResponse(String orderNumber, String skuCode, BigDecimal totalPrice, Integer quantity) {
}
