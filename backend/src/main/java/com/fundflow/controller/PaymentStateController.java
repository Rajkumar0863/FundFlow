package com.fundflow.controller;

import com.fundflow.entity.Payment;
import com.fundflow.entity.PaymentStatus;
import com.fundflow.service.PaymentNotFoundException;
import com.fundflow.service.PaymentStateMachineService;
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