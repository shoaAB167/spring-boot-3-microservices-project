package com.sh_inventory.microservice.inventoryservice.repository;

import com.sh_inventory.microservice.inventoryservice.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /** Derived query: Spring Data builds the SQL from the method name. */
    boolean existsBySkuCodeAndQuantityIsGreaterThanEqual(String skuCode, int quantity);

    Optional<Inventory> findFirstBySkuCode(String skuCode);

    /**
     * Adds stock in ONE atomic SQL statement: {@code UPDATE ... SET quantity = quantity + ?}.
     * <p>
     * The alternative, "read the row, add in Java, save", is a read-modify-write race: two
     * concurrent requests both read 10, both write 15, and 5 units vanish (a "lost update").
     * Letting the database do the arithmetic avoids that without any explicit locking.
     *
     * @return number of rows updated (0 means the SKU doesn't exist yet)
     */
    @Modifying(clearAutomatically = true)
    @Query("update Inventory i set i.quantity = i.quantity + :quantity where i.skuCode = :skuCode")
    int addStock(@Param("skuCode") String skuCode, @Param("quantity") int quantity);
}
