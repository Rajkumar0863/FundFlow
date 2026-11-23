package com.fundflow.service;

import com.fundflow.entity.Payment;
import com.fundflow.entity.PaymentStatus;
import com.fundflow.repository.PaymentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentStateMachineService {
    private final PaymentRepository repository;
    private final JdbcTemplate jdbcTemplate;

    public PaymentStateMachineService(PaymentRepository repository, JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Payment transition(UUID paymentId, PaymentStatus nextStatus) {
        if (nextStatus == null) {
            throw new IllegalArgumentException("Payment status is required");
        }
        // PostgreSQL row lock serializes simultaneous transitions for this payment.
        Payment payment = repository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
        PaymentStatus previous = payment.getStatus();
        payment.transitionTo(nextStatus);
        repository.saveAndFlush(payment);
        // Audit insert participates in the same database transaction.
        jdbcTemplate.update(
                "INSERT INTO payment_status_history (payment_id, from_status, to_status) VALUES (?, ?, ?)",
                paymentId, previous.name(), nextStatus.name());
        return payment;
    }
}