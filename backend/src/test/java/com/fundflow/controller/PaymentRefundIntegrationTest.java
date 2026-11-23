package com.fundflow.controller;

import com.fundflow.entity.Payment;
import com.fundflow.entity.PaymentStatus;
import com.fundflow.repository.PaymentRepository;
import com.fundflow.service.PaymentStateMachineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentRefundIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired PaymentRepository payments;
    @Autowired PaymentStateMachineService stateMachine;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Value("${spring.datasource.url}") String jdbcUrl;

    @BeforeEach
    void guardAndClean() throws Exception {
        assertEquals("jdbc:postgresql://localhost:5433/fundflow_test", jdbcUrl);
        try (Connection connection = dataSource.getConnection()) {
            assertEquals("fundflow_test", connection.getCatalog());
            assertEquals(jdbcUrl, connection.getMetaData().getURL());
        }
        payments.deleteAll();
    }

    private UUID create(boolean succeeded) {
        UUID id = payments.saveAndFlush(new Payment(new BigDecimal("100.00"), "EUR")).getId();
        if (succeeded) {
            stateMachine.transition(id, PaymentStatus.PROCESSING);
            stateMachine.transition(id, PaymentStatus.SUCCEEDED);
        }
        return id;
    }

    private org.springframework.test.web.servlet.ResultActions send(UUID id, String key, String amount) throws Exception {
        return mvc.perform(post("/api/v1/payments/{id}/refunds", id)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + "}"));
    }

    private BigDecimal refunded(UUID id) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM payment_refunds WHERE payment_id = ?",
                BigDecimal.class, id);
    }

    @Test
    void fullRefundSucceeds() throws Exception {
        UUID id = create(true);
        send(id, "full", "100.00").andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(100.0));
        assertEquals(0, refunded(id).compareTo(new BigDecimal("100.00")));
    }

    @Test
    void partialRefundsAndListingWork() throws Exception {
        UUID id = create(true);
        send(id, "first", "25.00").andExpect(status().isCreated());
        send(id, "second", "35.00").andExpect(status().isCreated());
        mvc.perform(get("/api/v1/payments/{id}/refunds", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        assertEquals(0, refunded(id).compareTo(new BigDecimal("60.00")));
    }

    @Test
    void cannotExceedOriginalAmount() throws Exception {
        UUID id = create(true);
        send(id, "first", "80.00").andExpect(status().isCreated());
        send(id, "second", "20.01").andExpect(status().isConflict());
        assertEquals(0, refunded(id).compareTo(new BigDecimal("80.00")));
    }

    @Test
    void pendingPaymentCannotBeRefunded() throws Exception {
        UUID id = create(false);
        send(id, "pending", "10.00").andExpect(status().isConflict());
        assertEquals(0, refunded(id).signum());
    }

    @Test
    void retryIsIdempotentAndDifferentAmountConflicts() throws Exception {
        UUID id = create(true);
        String first = send(id, "same", "25.00").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = send(id, "same", "25.00").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        assertEquals(mapper.readTree(first).get("id"), mapper.readTree(second).get("id"));
        send(id, "same", "26.00").andExpect(status().isConflict());
        assertEquals(0, refunded(id).compareTo(new BigDecimal("25.00")));
    }

    @Test
    void concurrentRefundsCannotOverRefund() throws Exception {
        UUID id = create(true);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> a = () -> { start.await(); return send(id, "a", "75.00")
                    .andReturn().getResponse().getStatus(); };
            Callable<Integer> b = () -> { start.await(); return send(id, "b", "75.00")
                    .andReturn().getResponse().getStatus(); };
            Future<Integer> fa = pool.submit(a);
            Future<Integer> fb = pool.submit(b);
            start.countDown();
            int x = fa.get(15, TimeUnit.SECONDS);
            int y = fb.get(15, TimeUnit.SECONDS);
            assertTrue((x == 201 && y == 409) || (x == 409 && y == 201),
                    "Expected 201 and 409, got " + x + " and " + y);
            assertEquals(0, refunded(id).compareTo(new BigDecimal("75.00")));
        } finally {
            pool.shutdownNow();
        }
    }
}