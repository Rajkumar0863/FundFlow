CREATE TABLE payments (
    id UUID PRIMARY KEY,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);