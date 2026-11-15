package com.sh_product.microservice.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body of POST /api/product. A record is an immutable data carrier: the compiler generates
 * the constructor, accessors (name(), price()...), equals/hashCode and toString.
 * Money uses BigDecimal, never double: 0.1 + 0.2 != 0.3 in binary floating point.
 */
public record ProductRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2000) String description,
        @NotNull @Positive BigDecimal price) {
}
