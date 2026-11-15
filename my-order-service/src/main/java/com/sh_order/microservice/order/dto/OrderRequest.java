package com.sh_order.microservice.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Body of POST /api/order. Validated by @Valid in the controller; invalid input gets a
 * 400 response listing each bad field.
 * <p>
 * Before validation was added, a request without "userDetails" passed the stock check, then
 * failed with a NullPointerException (500) while building the Kafka event.
 * <p>
 * The unused "id" field was removed: the server generates identifiers, so clients must not send them.
 * <p>
 * KNOWN GAP: the price comes from the client, which lets a caller choose their own price.
 * It should come from product-service. See docs/ROADMAP.md.
 */
public record OrderRequest(
        @NotBlank String skuCode,
        @NotNull @Positive BigDecimal price,
        @NotNull @Positive Integer quantity,
        @NotNull @Valid UserDetails userDetails) {

    public record UserDetails(
            @NotBlank @Email String email,
            @NotBlank String firstName,
            @NotBlank String lastName) {
    }
}
