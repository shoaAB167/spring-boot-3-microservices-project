package com.sh_product.microservice.product.controller;

import com.sh_product.microservice.product.dto.ProductRequest;
import com.sh_product.microservice.product.dto.ProductResponse;
import com.sh_product.microservice.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor // ✅ Ensures the constructor is generated
public class ProductController {

    private final ProductService productService; // ✅ This will be auto-injected by Spring

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@RequestBody ProductRequest productRequest) {
        return productService.createProduct(productRequest);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<ProductResponse> getAllProducts() {
        return productService.getAllProducts();
    }

}
