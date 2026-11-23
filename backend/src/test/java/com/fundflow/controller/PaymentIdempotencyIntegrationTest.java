package com.fundflow.controller;

import com.fundflow.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentIdempotencyIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired PaymentRepository repository;
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

    private org.springframework.test.web.servlet.ResultActions send(String key, String body) throws Exception {
        return mvc.perform(post("/api/v1/payments")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static final String PAYMENT = """
            {"amount":250.00,"currency":"EUR"}
            """;

    @Test
    void sameKeyAndPayloadReturnsOriginalPayment() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = send(key, PAYMENT).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = send(key, PAYMENT).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode firstJson =
    new com.fasterxml.jackson.databind.ObjectMapper().readTree(first);

com.fasterxml.jackson.databind.JsonNode secondJson =
    new com.fasterxml.jackson.databind.ObjectMapper().readTree(second);

assertEquals(firstJson.get("id"), secondJson.get("id"));
assertEquals(firstJson.get("amount"), secondJson.get("amount"));
assertEquals(firstJson.get("currency"), secondJson.get("currency"));
assertEquals(firstJson.get("status"), secondJson.get("status"));
assertEquals(firstJson.get("idempotencyKey"), secondJson.get("idempotencyKey"));
        assertEquals(1, repository.count());
    }

    @Test
    void sameKeyDifferentAmountReturns409() throws Exception {
        String key = UUID.randomUUID().toString();
        send(key, PAYMENT).andExpect(status().isCreated());
        send(key, "{\"amount\":251.00,\"currency\":\"EUR\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        assertEquals(1, repository.count());
    }

    @Test
    void sameKeyDifferentCurrencyReturns409() throws Exception {
        String key = UUID.randomUUID().toString();
        send(key, PAYMENT).andExpect(status().isCreated());
        send(key, "{\"amount\":250.00,\"currency\":\"USD\"}")
                .andExpect(status().isConflict());
        assertEquals(1, repository.count());
    }

    @Test
    void blankKeyReturns400() throws Exception {
        send(" ", PAYMENT).andExpect(status().isBadRequest());
        assertEquals(0, repository.count());
    }

    @Test
    void simultaneousSameKeyCreatesOnlyOnePayment() throws Exception {
        String key = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> task = () -> {
                start.await();
                return send(key, PAYMENT).andReturn().getResponse().getStatus();
            };
            Future<Integer> first = pool.submit(task);
            Future<Integer> second = pool.submit(task);
            start.countDown();
            int a = first.get(15, TimeUnit.SECONDS);
            int b = second.get(15, TimeUnit.SECONDS);
            assertTrue((a == 201 && b == 200) || (a == 200 && b == 201),
                    "Expected one 201 and one 200, got " + a + " and " + b);
            assertEquals(1, repository.count());
        } finally {
            pool.shutdownNow();
        }
    }
}
