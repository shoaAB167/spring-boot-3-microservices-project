package com.sh_inventory.microservice.inventoryservice.dto;

/** Stock level of one SKU after an update. */
public record InventoryResponse(String skuCode, Integer quantity) {
}
