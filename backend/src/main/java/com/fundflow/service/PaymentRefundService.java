package com.fundflow.service;

import com.fundflow.entity.Payment;
import com.fundflow.entity.PaymentStatus;
import com.fundflow.repository.PaymentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentRefundService {
    public record Refund(UUID id, UUID paymentId, BigDecimal amount,
                         String idempotencyKey, Instant createdAt) {}
    public record Result(Refund refund, boolean created) {}

    private final PaymentRepository payments;
    private final JdbcTemplate jdbc;

    public PaymentRefundService(PaymentRepository payments, JdbcTemplate jdbc) {
        this.payments = payments;
        this.jdbc = jdbc;
    }

    private static Refund mapRefund(ResultSet rs, int rowNum) throws SQLException {
        return new Refund((UUID) rs.getObject("id"),
                (UUID) rs.getObject("payment_id"), rs.getBigDecimal("amount"),
                rs.getString("idempotency_key"),
                rs.getTimestamp("created_at").toInstant());
    }

    @Transactional
    public Result refund(UUID paymentId, BigDecimal requestedAmount, String key) {
        if (requestedAmount == null || requestedAmount.signum() <= 0 ||
                requestedAmount.scale() > 2 ||
                requestedAmount.precision() - requestedAmount.scale() > 17) {
            throw new IllegalArgumentException("Refund amount must be positive and fit NUMERIC(19,2)");
        }
        if (key == null || key.isBlank() || key.length() > 255 || !key.equals(key.trim())) {
            throw new IllegalArgumentException("Idempotency-Key must be 1-255 characters without surrounding whitespace");
        }
        BigDecimal amount = requestedAmount.setScale(2);

        // Lock the parent payment before inspecting refunds. This serializes
        // concurrent refunds for the same payment, including distinct keys.
        Payment payment = payments.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));

        List<Refund> prior = jdbc.query("""
                SELECT id, payment_id, amount, idempotency_key, created_at
                FROM payment_refunds WHERE payment_id = ? AND idempotency_key = ?
                """, PaymentRefundService::mapRefund, paymentId, key);
        if (!prior.isEmpty()) {
            Refund existing = prior.get(0);
            if (existing.amount().compareTo(amount) != 0) {
                throw new IllegalStateException("Idempotency key already used with a different refund amount");
            }
            return new Result(existing, false);
        }

        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException("Only SUCCEEDED payments can be refunded");
        }
        BigDecimal alreadyRefunded = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM payment_refunds WHERE payment_id = ?",
                BigDecimal.class, paymentId);
        if (alreadyRefunded == null) alreadyRefunded = BigDecimal.ZERO;
        if (alreadyRefunded.add(amount).compareTo(payment.getAmount()) > 0) {
            throw new IllegalStateException("Refund exceeds remaining payment amount");
        }

        UUID refundId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO payment_refunds (id, payment_id, amount, idempotency_key)
                VALUES (?, ?, ?, ?)
                """, refundId, paymentId, amount, key);
        Refund created = jdbc.queryForObject("""
                SELECT id, payment_id, amount, idempotency_key, created_at
                FROM payment_refunds WHERE id = ?
                """, PaymentRefundService::mapRefund, refundId);
        return new Result(created, true);
    }

    @Transactional(readOnly = true)
    public List<Refund> list(UUID paymentId) {
        if (!payments.existsById(paymentId)) {
            throw new PaymentNotFoundException("Payment not found: " + paymentId);
        }
        return jdbc.query("""
                SELECT id, payment_id, amount, idempotency_key, created_at
                FROM payment_refunds WHERE payment_id = ? ORDER BY created_at, id
                """, PaymentRefundService::mapRefund, paymentId);
    }
}