package com.sh_inventory.microservice.inventoryservice.controller;

import com.sh_inventory.microservice.inventoryservice.model.Inventory;
import com.sh_inventory.microservice.inventoryservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public boolean isInStock(@RequestParam String skuCode, @RequestParam Integer quantity) {
        return inventoryService.isInStock(skuCode, quantity);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void addInventory(@RequestBody Inventory inventory) {
        inventoryService.addInventory(inventory);
    }
}