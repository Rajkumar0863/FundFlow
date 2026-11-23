package com.fundflow.controller;

import com.fundflow.entity.Payment;
import com.fundflow.service.IdempotentPaymentService;
import com.fundflow.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService paymentService;
    private final IdempotentPaymentService idempotentPaymentService;

    public PaymentController(PaymentService paymentService,
                             IdempotentPaymentService idempotentPaymentService) {
        this.paymentService = paymentService;
        this.idempotentPaymentService = idempotentPaymentService;
    }

    public record CreatePaymentRequest(BigDecimal amount, String currency) {}

    @PostMapping
    public ResponseEntity<Payment> createPayment(
            @RequestBody CreatePaymentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        try {
            if (key == null) {
                // Keep legacy behavior until all clients migrate to keyed requests.
                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(paymentService.createPayment(request.amount(), request.currency()));
            }
            IdempotentPaymentService.Result result =
                    idempotentPaymentService.create(request.amount(), request.currency(), key);
            return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                    .body(result.payment());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Payment getPayment(@PathVariable UUID id) {
        try {
            return paymentService.getPayment(id);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @GetMapping
    public List<Payment> getAllPayments() {
        return paymentService.getAllPayments();
    }
}
