
-- FundFlow Database Migration V1
-- Create the payments table

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

-- Improve queries that filter by payment status
CREATE INDEX idx_payments_status
ON payments(status);

-- Improve queries that sort by creation time
CREATE INDEX idx_payments_created_at
ON payments(created_at);
