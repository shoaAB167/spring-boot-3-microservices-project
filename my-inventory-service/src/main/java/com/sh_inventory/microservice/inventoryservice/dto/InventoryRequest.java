package com.sh_inventory.microservice.inventoryservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Body of POST /api/inventory: "add this many units of this SKU".
 * <p>
 * The endpoint used to accept the JPA entity {@code Inventory} directly. That is a
 * <b>mass assignment</b> vulnerability (OWASP API Top 10, API3:2023): a caller could send
 * {@code "id": 1} and overwrite any existing row, or send negative quantities. A request DTO
 * exposes only the fields a client is allowed to set, and Bean Validation rejects bad values.
 */
public record InventoryRequest(@NotBlank String skuCode, @Positive int quantity) {
}
