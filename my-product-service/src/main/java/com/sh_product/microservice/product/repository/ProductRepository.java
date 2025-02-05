package com.sh_product.microservice.product.repository;


import com.sh_product.microservice.product.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductRepository extends MongoRepository<Product, String> {
}