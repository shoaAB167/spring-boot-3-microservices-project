package com.sh_order.microservice.order.controller;

import com.sh_order.microservice.order.dto.OrderRequest;
import com.sh_order.microservice.order.dto.OrderResponse;
import com.sh_order.microservice.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * Responses:
     * 201 order created |
     * 400 invalid body |
     * 409 not enough stock |
     * 503 inventory-service unavailable.
     * See exception/GlobalExceptionHandler.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse placeOrder(@Valid @RequestBody OrderRequest orderRequest) {
        return orderService.placeOrder(orderRequest);
    }
}
