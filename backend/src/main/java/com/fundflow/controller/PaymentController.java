
package com.fundflow.controller;

import com.fundflow.entity.Payment;
import com.fundflow.service.PaymentService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public record CreatePaymentRequest(
        BigDecimal amount,
        String currency
    ) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Payment createPayment(
            @RequestBody CreatePaymentRequest request) {

        try {
            return paymentService.createPayment(
                request.amount(),
                request.currency()
            );
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, e.getMessage()
            );
        }
    }

    @GetMapping("/{id}")
    public Payment getPayment(@PathVariable UUID id) {

        try {
            return paymentService.getPayment(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND, e.getMessage()
            );
        }
    }

    @GetMapping
    public List<Payment> getAllPayments() {
        return paymentService.getAllPayments();
    }
}
