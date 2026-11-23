CREATE TABLE payment_refunds (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    idempotency_key VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payment_refund_idempotency UNIQUE (payment_id, idempotency_key)
);

CREATE INDEX idx_payment_refunds_payment_id ON payment_refunds(payment_id, created_at);