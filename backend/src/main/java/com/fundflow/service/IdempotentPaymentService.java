package com.fundflow.service;

import com.fundflow.entity.Payment;
import com.fundflow.exception.IdempotencyConflictException;
import com.fundflow.repository.PaymentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

@Service
public class IdempotentPaymentService {
    public record Result(Payment payment, boolean created) {}

    private final PaymentRepository repository;
    private final TransactionTemplate transaction;

    public IdempotentPaymentService(PaymentRepository repository,
                                    PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Result create(BigDecimal amount, String currency, String key) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2 ||
                amount.precision() - amount.scale() > 17) {
            throw new IllegalArgumentException("Amount must be positive and fit NUMERIC(19,2)");
        }
        if (currency == null || !currency.matches("(?i)[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency must be a three-letter code");
        }
        if (key == null || key.isBlank() || key.length() > 255 || !key.equals(key.trim())) {
            throw new IllegalArgumentException("Idempotency-Key must be 1-255 characters without surrounding whitespace");
        }
        String normalizedCurrency = currency.toUpperCase(Locale.ROOT);
        BigDecimal normalizedAmount = amount.setScale(2);

        try {
            return transaction.execute(status -> {
                Optional<Payment> existing = repository.findByIdempotencyKey(key);
                if (existing.isPresent()) {
                    return matched(existing.get(), normalizedAmount, normalizedCurrency, false);
                }
                Payment created = repository.saveAndFlush(new Payment(normalizedAmount, normalizedCurrency, key));
                return new Result(created, true);
            });
        } catch (DataIntegrityViolationException collision) {
            // The failed insert's transaction has already rolled back. PostgreSQL's
            // unique index serializes concurrent inserts of the same key.
            Result recovered = transaction.execute(status ->
                    repository.findByIdempotencyKey(key)
                            .map(payment -> matched(payment, normalizedAmount, normalizedCurrency, false))
                            .orElse(null));
            if (recovered != null) return recovered;
            throw collision; // A different integrity violation: don't disguise it.
        }
    }

    private Result matched(Payment payment, BigDecimal amount, String currency, boolean created) {
        if (payment.getAmount().compareTo(amount) != 0 || !payment.getCurrency().equals(currency)) {
            throw new IdempotencyConflictException("Key already used for a different payment payload");
        }
        return new Result(payment, created);
    }
}
