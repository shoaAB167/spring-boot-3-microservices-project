package com.sh_inventory.microservice.inventoryservice.repository;

import com.sh_inventory.microservice.inventoryservice.model.Inventory;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    boolean existsBySkuCodeAndQuantityIsGreaterThanEqual(String skuCode, int quantity);
}

