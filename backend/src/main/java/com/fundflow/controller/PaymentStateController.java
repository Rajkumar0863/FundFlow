package com.fundflow.controller;

import com.fundflow.entity.Payment;
import com.fundflow.entity.PaymentStatus;
import com.fundflow.service.PaymentNotFoundException;
import com.fundflow.service.PaymentStateMachineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentStateController {
    private final PaymentStateMachineService stateMachine;

    public PaymentStateController(PaymentStateMachineService stateMachine) {
        this.stateMachine = stateMachine;
    }

    public record TransitionRequest(PaymentStatus status) {}

    @Operation(
        summary = "Transition a payment to a new status",
        description = "Applies a validated payment lifecycle transition and records its audit history."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Payment status updated"),
        @ApiResponse(responseCode = "400", description = "Invalid or missing payment status"),
        @ApiResponse(responseCode = "404", description = "Payment not found"),
        @ApiResponse(responseCode = "409", description = "Invalid payment state transition")
    })
    @PatchMapping("/{id}/status")
    public Payment changeStatus(@PathVariable UUID id, @RequestBody TransitionRequest request) {
        if (request == null || request.status() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment status is required");
        }
        try {
            return stateMachine.transition(id, request.status());
        } catch (PaymentNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }
}