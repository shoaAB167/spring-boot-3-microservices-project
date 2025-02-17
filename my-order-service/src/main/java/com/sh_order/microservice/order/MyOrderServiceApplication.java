package com.sh_order.microservice.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class MyOrderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(MyOrderServiceApplication.class, args);
	}

}
