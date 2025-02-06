package com.sh_order.microservice.order;

import org.springframework.boot.SpringApplication;

public class TestMyOrderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(MyOrderServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
