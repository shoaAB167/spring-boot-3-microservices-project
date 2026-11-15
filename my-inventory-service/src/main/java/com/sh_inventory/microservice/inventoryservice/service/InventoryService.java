package com.sh_inventory.microservice.inventoryservice.service;

import com.sh_inventory.microservice.inventoryservice.dto.InventoryRequest;
import com.sh_inventory.microservice.inventoryservice.dto.InventoryResponse;
import com.sh_inventory.microservice.inventoryservice.model.Inventory;
import com.sh_inventory.microservice.inventoryservice.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    /** readOnly = true lets Hibernate skip dirty checking and lets the driver route to a replica. */
    @Transactional(readOnly = true)
    public boolean isInStock(String skuCode, Integer quantity) {
        return inventoryRepository.existsBySkuCodeAndQuantityIsGreaterThanEqual(skuCode, quantity);
    }

    /**
     * Adds stock for a SKU, creating the SKU if it is new.
     * Previously every call inserted a new row, so the same SKU ended up spread over several
     * rows and the stock check (which looks at one row at a time) gave wrong answers.
     * <p>
     * KNOWN GAP: two concurrent requests for a brand-new SKU can still both insert. The real fix
     * is a UNIQUE constraint on sku_code (a new Flyway migration, after merging any duplicate
     * rows already in the table). See docs/ROADMAP.md.
     */
    @Transactional
    public InventoryResponse addInventory(InventoryRequest request) {
        int updated = inventoryRepository.addStock(request.skuCode(), request.quantity());
        if (updated == 0) {
            inventoryRepository.save(new Inventory(null, request.skuCode(), request.quantity()));
        }
        Inventory current = inventoryRepository.findFirstBySkuCode(request.skuCode()).orElseThrow();
        log.info("Stock for {} is now {}", current.getSkuCode(), current.getQuantity());
        return new InventoryResponse(current.getSkuCode(), current.getQuantity());
    }
}
