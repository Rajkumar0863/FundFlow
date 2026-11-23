package com.fundflow.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "idempotency_key", length = 255, unique = true)
    private String idempotencyKey;

    protected Payment() {}

    public Payment(BigDecimal amount, String currency) {
        this(amount, currency, null);
    }

    public Payment(BigDecimal amount, String currency, String idempotencyKey) {
        this.amount = amount;
        this.currency = currency;
        this.status = PaymentStatus.PENDING;
        this.createdAt = Instant.now();
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getId() { return id; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public void transitionTo(PaymentStatus nextStatus) {
        if (nextStatus == null) {
            throw new IllegalArgumentException("Payment status is required");
        }
        boolean allowed = switch (status) {
            case PENDING -> nextStatus == PaymentStatus.PROCESSING || nextStatus == PaymentStatus.CANCELLED;
            case PROCESSING -> nextStatus == PaymentStatus.SUCCEEDED || nextStatus == PaymentStatus.FAILED;
            case SUCCEEDED, FAILED, CANCELLED -> false;
        };
        if (!allowed) {
            throw new IllegalStateException("Invalid payment transition: " + status + " -> " + nextStatus);
        }
        this.status = nextStatus;
    }
}