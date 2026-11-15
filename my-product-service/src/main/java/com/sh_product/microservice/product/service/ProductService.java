package com.sh_product.microservice.product.service;

import com.sh_product.microservice.product.dto.ProductRequest;
import com.sh_product.microservice.product.dto.ProductResponse;
import com.sh_product.microservice.product.model.Product;
import com.sh_product.microservice.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;

    public ProductResponse createProduct(ProductRequest productRequest) {
        Product product = Product.builder()
                .name(productRequest.name())
                .description(productRequest.description())
                .price(productRequest.price())
                .build();

        // Use the instance save() returns: that is the contract, and it carries the generated id.
        Product saved = productRepository.save(product);
        log.info("Product {} is saved", saved.getId());
        return mapToProductResponse(saved);
    }

    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream().map(this::mapToProductResponse).toList();
    }

    private ProductResponse mapToProductResponse(Product product) {
        return new ProductResponse(product.getId(), product.getName(),
                product.getDescription(), product.getPrice());
    }
}
