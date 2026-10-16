
package com.fundflow.service;

import com.fundflow.entity.Payment;
import com.fundflow.repository.PaymentRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public Payment createPayment(BigDecimal amount, String currency) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                "Payment amount must be greater than zero"
            );
        }

        if (currency == null ||
            !currency.matches("(?i)[A-Z]{3}")) {
            throw new IllegalArgumentException(
                "Currency must be a three-letter code"
            );
        }

        Payment payment = new Payment(
            amount,
            currency.toUpperCase(Locale.ROOT)
        );

        return paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public Payment getPayment(UUID id) {
        return paymentRepository.findById(id)
            .orElseThrow(() ->
                new IllegalArgumentException(
                    "Payment not found: " + id
                )
            );
    }

    @Transactional(readOnly = true)
    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }
}
