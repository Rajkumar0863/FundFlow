package com.fundflow.controller;

import com.fundflow.service.PaymentNotFoundException;
import com.fundflow.service.PaymentRefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    @Operation(
        summary = "Create a payment refund",
        description = "Creates a refund for an eligible payment. Supports idempotent retries and protects against concurrent over-refunding."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Refund created"),
        @ApiResponse(responseCode = "200", description = "Existing refund returned for an idempotent replay"),
        @ApiResponse(responseCode = "400", description = "Invalid refund request"),
        @ApiResponse(responseCode = "404", description = "Payment not found"),
        @ApiResponse(responseCode = "409", description = "Refund conflicts with payment state, available balance or idempotency requirements")
    })
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

    @Operation(summary = "List refunds for a payment")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Refunds returned"),
        @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    @GetMapping
    public List<PaymentRefundService.Refund> list(@PathVariable UUID paymentId) {
        try {
            return service.list(paymentId);
        } catch (PaymentNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }
}