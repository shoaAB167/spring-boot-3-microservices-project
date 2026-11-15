package com.sh_inventory.microservice.inventoryservice;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real app against a real MySQL in Docker (Testcontainers). Flyway applies
 * V1 (table) and V2 (seed data: 100 x iphone_15 ...) on start-up, exactly like production.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryServiceApplicationTests {

	@ServiceConnection
	static MySQLContainer<?> mySQLContainer = new MySQLContainer<>("mysql:8.3.0");

	static {
		mySQLContainer.start();
	}

	@LocalServerPort
	private Integer port;

	@BeforeEach
	void setup() {
		RestAssured.baseURI = "http://localhost";
		RestAssured.port = port;
	}

	@Test
	void shouldReadInventory() {
		assertTrue(isInStock("iphone_15", 1));
		assertFalse(isInStock("iphone_15", 1000));
	}

	@Test
	void shouldCreateNewSkuThenIncrementExistingSku() {
		addStock("pixel_9", 5).statusCode(201).body("quantity", equalTo(5));
		// Same SKU again: stock is added to the existing row, not inserted as a duplicate.
		addStock("pixel_9", 7).statusCode(201).body("quantity", equalTo(12));

		assertTrue(isInStock("pixel_9", 12));
		assertFalse(isInStock("pixel_9", 13));
	}

	@Test
	void shouldRejectInvalidInventoryRequest() {
		RestAssured.given()
				.contentType("application/json")
				.body("""
						{ "id": 1, "skuCode": "", "quantity": -5 }
						""")
				.when()
				.post("/api/inventory")
				.then()
				.statusCode(400)
				.contentType("application/problem+json")
				.body("title", notNullValue());
	}

	@Test
	void shouldRejectInvalidStockQuery() {
		RestAssured.given()
				.when()
				.get("/api/inventory?skuCode=iphone_15&quantity=-1")
				.then()
				.statusCode(400);
	}

	private static boolean isInStock(String skuCode, int quantity) {
		return RestAssured.given()
				.queryParam("skuCode", skuCode)
				.queryParam("quantity", quantity)
				.when()
				.get("/api/inventory")
				.then()
				.statusCode(200)
				.extract().as(Boolean.class);
	}

	private static io.restassured.response.ValidatableResponse addStock(String skuCode, int quantity) {
		return RestAssured.given()
				.contentType("application/json")
				.body("""
						{ "skuCode": "%s", "quantity": %d }
						""".formatted(skuCode, quantity))
				.when()
				.post("/api/inventory")
				.then();
	}
}
