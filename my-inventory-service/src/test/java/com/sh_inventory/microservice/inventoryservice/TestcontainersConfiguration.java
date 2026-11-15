package com.sh_inventory.microservice.inventoryservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Used by TestInventoryServiceApplication to run the app locally against a throwaway MySQL. */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	MySQLContainer<?> mysqlContainer() {
		// Pinned: "latest" changes underneath you and makes builds non-reproducible.
		return new MySQLContainer<>(DockerImageName.parse("mysql:8.3.0"));
	}

}
