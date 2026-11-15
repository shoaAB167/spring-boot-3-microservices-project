package com.sh_order.microservice.order.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns exceptions into consistent JSON error responses using RFC 9457 "Problem Details"
 * ({@code application/problem+json}), which Spring 6 supports natively via {@link ProblemDetail}.
 * <p>
 * Extending {@link ResponseEntityExceptionHandler} gives Problem Detail responses for all of
 * Spring MVC's own exceptions (bad JSON, missing parameters, validation...) for free.
 * <p>
 * Without this class every failure, including "out of stock", was a 500 Internal Server Error
 * with a stack-trace-shaped body: callers could not tell a business rule from a crash.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ProductOutOfStockException.class)
    public ProblemDetail handleOutOfStock(ProductOutOfStockException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Product out of stock");
        return problem;
    }

    @ExceptionHandler(InventoryServiceUnavailableException.class)
    public ProblemDetail handleInventoryUnavailable(InventoryServiceUnavailableException ex) {
        log.error("Order rejected because inventory-service is unavailable", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Stock could not be checked right now, please try again later");
        problem.setTitle("Inventory service unavailable");
        return problem;
    }

    /** Bean Validation failures (@Valid): 400 with one message per invalid field. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Invalid request");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }
}
