
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private DataSource dataSource;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    private static final String TEST_DATABASE_URL =
        "jdbc:postgresql://localhost:5433/fundflow_test";

    @BeforeEach
    void setUp() throws Exception {

        // Verify the configured test database URL
        assertEquals(
            TEST_DATABASE_URL,
            datasourceUrl,
            "SAFETY ERROR: Tests must use fundflow_test!"
        );

        // Verify the actual PostgreSQL connection
        try (Connection connection = dataSource.getConnection()) {

            assertEquals(
                "fundflow_test",
                connection.getCatalog(),
                "SAFETY ERROR: Connected to the wrong database!"
            );

            assertEquals(
                TEST_DATABASE_URL,
                connection.getMetaData().getURL(),
                "SAFETY ERROR: Unexpected JDBC URL!"
            );
        }

        // Delete only test database payment records
        paymentRepository.deleteAll();
    }

    // TEST 1: Create a valid payment
    @Test
    void createPaymentReturns201() throws Exception {

        mockMvc.perform(
                post("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "amount": 250.00,
                          "currency": "EUR"
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.amount").value(250.00))
            .andExpect(jsonPath("$.currency").value("EUR"))
            .andExpect(jsonPath("$.status").value("PENDING"));

        assertEquals(
            1,
            paymentRepository.count(),
            "Payment must be saved in PostgreSQL"
        );
    }

    // TEST 2: Reject negative payment amounts
    @Test
    void negativeAmountReturns400() throws Exception {

        mockMvc.perform(
                post("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "amount": -50,
                          "currency": "EUR"
                        }
                        """)
            )
            .andExpect(status().isBadRequest());

        assertEquals(
            0,
            paymentRepository.count(),
            "Invalid payments must not be saved"
        );
    }

    // TEST 3: Return 404 for nonexistent payment
    @Test
    void missingPaymentReturns404() throws Exception {

        UUID missingId = UUID.randomUUID();

        mockMvc.perform(
                get("/api/v1/payments/{id}", missingId)
            )
            .andExpect(status().isNotFound());
    }

    // TEST 4: Return an empty payment list
    @Test
    void getAllPaymentsReturns200() throws Exception {

        mockMvc.perform(
                get("/api/v1/payments")
            )
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
    }
}
