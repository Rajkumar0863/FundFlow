package com.fundflow.controller;

import com.fundflow.entity.Payment;
import com.fundflow.repository.PaymentRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentStateMachineIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired PaymentRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Value("${spring.datasource.url}") String jdbcUrl;

    @BeforeEach
    void cleanTestDatabase() throws Exception {
        assertEquals("jdbc:postgresql://localhost:5433/fundflow_test", jdbcUrl);
        try (Connection connection = dataSource.getConnection()) {
            assertEquals("fundflow_test", connection.getCatalog());
            assertEquals(jdbcUrl, connection.getMetaData().getURL());
        }
        repository.deleteAll();
    }

    private UUID createPayment() {
        return repository.saveAndFlush(new Payment(new BigDecimal("42.00"), "EUR")).getId();
    }

    private org.springframework.test.web.servlet.ResultActions transition(UUID id, String status) throws Exception {
        return mvc.perform(patch("/api/v1/payments/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}"));
    }

    private int auditCount(UUID id) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM payment_status_history WHERE payment_id = ?", Integer.class, id);
        return count == null ? 0 : count;
    }

    @Test
    void pendingToProcessingThenSucceededWritesTwoAuditRows() throws Exception {
        UUID id = createPayment();
        transition(id, "PROCESSING").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
        transition(id, "SUCCEEDED").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        assertEquals("SUCCEEDED", repository.findById(id).orElseThrow().getStatus().name());
        assertEquals(2, auditCount(id));
    }

    @Test
    void pendingCanBeCancelled() throws Exception {
        UUID id = createPayment();
        transition(id, "CANCELLED").andExpect(status().isOk());
        assertEquals(1, auditCount(id));
    }

    @Test
    void invalidTransitionReturns409AndDoesNotWriteAudit() throws Exception {
        UUID id = createPayment();
        transition(id, "SUCCEEDED").andExpect(status().isConflict());
        assertEquals("PENDING", repository.findById(id).orElseThrow().getStatus().name());
        assertEquals(0, auditCount(id));
    }

    @Test
    void missingPaymentReturns404() throws Exception {
        transition(UUID.randomUUID(), "PROCESSING").andExpect(status().isNotFound());
    }

    @Test
    void terminalStatusCannotChange() throws Exception {
        UUID id = createPayment();
        transition(id, "CANCELLED").andExpect(status().isOk());
        transition(id, "PROCESSING").andExpect(status().isConflict());
        assertEquals(1, auditCount(id));
    }

    @Test
    void concurrentTransitionsCannotBothWin() throws Exception {
        UUID id = createPayment();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> task = () -> {
                start.await();
                return transition(id, "PROCESSING").andReturn().getResponse().getStatus();
            };
            Future<Integer> a = pool.submit(task);
            Future<Integer> b = pool.submit(task);
            start.countDown();
            int first = a.get(15, TimeUnit.SECONDS);
            int second = b.get(15, TimeUnit.SECONDS);
            assertTrue((first == 200 && second == 409) || (first == 409 && second == 200),
                    "Expected one 200 and one 409, got " + first + " and " + second);
            assertEquals(1, auditCount(id));
            assertEquals("PROCESSING", repository.findById(id).orElseThrow().getStatus().name());
        } finally {
            pool.shutdownNow();
        }
    }
}