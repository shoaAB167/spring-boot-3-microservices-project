package com.sh_inventory.microservice.inventoryservice.controller;

import com.sh_inventory.microservice.inventoryservice.dto.InventoryRequest;
import com.sh_inventory.microservice.inventoryservice.dto.InventoryResponse;
import com.sh_inventory.microservice.inventoryservice.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    /**
     * Called synchronously by order-service before it accepts an order.
     * The constraint annotations on the parameters are checked automatically by Spring 6.1+
     * (built-in method validation); invalid values get 400.
     */
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public boolean isInStock(@RequestParam @NotBlank String skuCode, @RequestParam @Positive Integer quantity) {
        return inventoryService.isInStock(skuCode, quantity);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryResponse addInventory(@Valid @RequestBody InventoryRequest request) {
        return inventoryService.addInventory(request);
    }
}
