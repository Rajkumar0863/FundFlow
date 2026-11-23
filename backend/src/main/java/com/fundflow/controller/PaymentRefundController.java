package com.fundflow.controller;

import com.fundflow.service.PaymentNotFoundException;
import com.fundflow.service.PaymentRefundService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments/{paymentId}/refunds")
public class PaymentRefundController {
    private final PaymentRefundService service;

    public PaymentRefundController(PaymentRefundService service) {
        this.service = service;
    }

    public record CreateRefundRequest(BigDecimal amount) {}

    @PostMapping
    public ResponseEntity<PaymentRefundService.Refund> create(
            @PathVariable UUID paymentId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody CreateRefundRequest request) {
        try {
            PaymentRefundService.Result result = service.refund(
                    paymentId, request == null ? null : request.amount(), key);
            return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                    .body(result.refund());
        } catch (PaymentNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping
    public List<PaymentRefundService.Refund> list(@PathVariable UUID paymentId) {
        try {
            return service.list(paymentId);
        } catch (PaymentNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }
}