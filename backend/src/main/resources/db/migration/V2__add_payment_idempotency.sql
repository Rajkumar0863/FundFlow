-- V2: Add idempotency support to payments
-- Existing payment records remain unchanged.

ALTER TABLE payments
ADD COLUMN idempotency_key VARCHAR(255);

-- Prevent duplicate non-null idempotency keys.
-- PostgreSQL permits multiple NULL values in a
-- unique constraint, preserving legacy requests.

ALTER TABLE payments
ADD CONSTRAINT uq_payments_idempotency_key
UNIQUE (idempotency_key);