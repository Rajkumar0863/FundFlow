
package com.fundflow.service;

import com.fundflow.entity.Payment;
import com.fundflow.repository.PaymentRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    private Payment samplePayment;

    @BeforeEach
    void setUp() {
        samplePayment = new Payment(
            new BigDecimal("250.00"), "EUR"
        );
    }

    @Test
    void shouldCreateValidPayment() {
        when(paymentRepository.save(any(Payment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Payment result = paymentService.createPayment(
            new BigDecimal("250.00"), "eur"
        );

        assertNotNull(result);
        assertEquals(new BigDecimal("250.00"), result.getAmount());
        assertEquals("EUR", result.getCurrency());

        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () ->
            paymentService.createPayment(
                new BigDecimal("-50"), "EUR"
            )
        );

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void shouldRejectZeroAmount() {
        assertThrows(IllegalArgumentException.class, () ->
            paymentService.createPayment(
                BigDecimal.ZERO, "EUR"
            )
        );

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void shouldRejectNullAmount() {
        assertThrows(IllegalArgumentException.class, () ->
            paymentService.createPayment(null, "EUR")
        );

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void shouldRejectInvalidCurrency() {
        assertThrows(IllegalArgumentException.class, () ->
            paymentService.createPayment(
                BigDecimal.TEN, "EURO"
            )
        );

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void shouldRejectNullCurrency() {
        assertThrows(IllegalArgumentException.class, () ->
            paymentService.createPayment(
                BigDecimal.TEN, null
            )
        );

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void shouldRetrieveExistingPayment() {
        UUID id = UUID.randomUUID();

        when(paymentRepository.findById(id))
            .thenReturn(Optional.of(samplePayment));

        Payment result = paymentService.getPayment(id);

        assertSame(samplePayment, result);
        verify(paymentRepository).findById(id);
    }

    @Test
    void shouldRejectMissingPayment() {
        UUID id = UUID.randomUUID();

        when(paymentRepository.findById(id))
            .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
            paymentService.getPayment(id)
        );
    }

    @Test
    void shouldRetrieveAllPayments() {
        when(paymentRepository.findAll())
            .thenReturn(List.of(samplePayment));

        List<Payment> results = paymentService.getAllPayments();

        assertEquals(1, results.size());
        assertSame(samplePayment, results.get(0));
    }
}
