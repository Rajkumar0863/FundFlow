package com.fundflow.controller;

import com.fundflow.exception.IdempotencyConflictException;
import com.fundflow.service.PaymentNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class PaymentExceptionHandler {

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiError> handleIdempotencyConflict(
            IdempotencyConflictException ex) {

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(
                        "IDEMPOTENCY_CONFLICT",
                        ex.getMessage(),
                        409
                ));
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ApiError> handlePaymentNotFound(
            PaymentNotFoundException ex) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(
                        "PAYMENT_NOT_FOUND",
                        ex.getMessage(),
                        404
                ));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(
            ResponseStatusException ex) {

        HttpStatus status = HttpStatus.resolve(
                ex.getStatusCode().value()
        );

        String code = switch (ex.getStatusCode().value()) {
            case 400 -> "BAD_REQUEST";
            case 404 -> "RESOURCE_NOT_FOUND";
            case 409 -> "INVALID_STATE_TRANSITION";
            default -> "HTTP_ERROR";
        };

        return ResponseEntity.status(ex.getStatusCode())
                .body(new ApiError(
                        code,
                        ex.getReason() != null
                                ? ex.getReason()
                                : "Request failed",
                        ex.getStatusCode().value()
                ));
    }
}