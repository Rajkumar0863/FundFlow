
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

    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private Instant createdAt;

    // Optional idempotency key to prevent duplicate payments
    @Column(
        name = "idempotency_key",
        length = 255,
        unique = true
    )
    private String idempotencyKey;

    // Required by JPA
    protected Payment() {
    }

    // Existing constructor - preserves compatibility
    public Payment(BigDecimal amount, String currency) {
        this(amount, currency, null);
    }

    // New constructor with idempotency support
    public Payment(
            BigDecimal amount,
            String currency,
            String idempotencyKey
    ) {
        this.amount = amount;
        this.currency = currency;
        this.status = PaymentStatus.PENDING;
        this.createdAt = Instant.now();
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
