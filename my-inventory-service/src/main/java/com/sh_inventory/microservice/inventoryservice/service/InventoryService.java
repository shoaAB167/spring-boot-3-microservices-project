package com.sh_inventory.microservice.inventoryservice.service;

import com.sh_inventory.microservice.inventoryservice.model.Inventory;
import com.sh_inventory.microservice.inventoryservice.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Transactional(readOnly = true)
    public boolean isInStock(String skuCode, Integer quantity) {
        return inventoryRepository.existsBySkuCodeAndQuantityIsGreaterThanEqual(skuCode, quantity);
    }

    @Transactional
    public void addInventory(Inventory inventory) {
        inventoryRepository.save(inventory);
    }
}