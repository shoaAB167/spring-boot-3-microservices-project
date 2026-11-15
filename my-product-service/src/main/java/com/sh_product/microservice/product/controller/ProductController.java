package com.sh_product.microservice.product.controller;

import com.sh_product.microservice.product.dto.ProductRequest;
import com.sh_product.microservice.product.dto.ProductResponse;
import com.sh_product.microservice.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor // generates a constructor for the final fields = constructor injection
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@Valid @RequestBody ProductRequest productRequest) {
        return productService.createProduct(productRequest);
    }

    /**
     * KNOWN GAP: returns every product in one response. With a large catalogue this needs
     * pagination (Spring Data's Pageable). See docs/ROADMAP.md.
     */
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<ProductResponse> getAllProducts() {
        return productService.getAllProducts();
    }
}
