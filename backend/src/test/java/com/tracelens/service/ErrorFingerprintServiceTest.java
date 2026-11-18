package com.tracelens.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ErrorFingerprintServiceTest {

    private ErrorFingerprintService fingerprintService;

    @BeforeEach
    void setUp() {
        fingerprintService = new ErrorFingerprintService();
    }

    @Test
    void normalizesDynamicUuidsAndNumbers() {
        String msg1 = "Order 49204 failed for user 5518 with txId 123e4567-e89b-12d3-a456-426614174000 at 2026-10-24T12:00:00Z";
        String msg2 = "Order 99182 failed for user 1192 with txId 987fcdeb-51a2-43d7-9876-543210abcdef at 2026-10-24T15:30:00Z";

        String norm1 = fingerprintService.normalizeMessage(msg1);
        String norm2 = fingerprintService.normalizeMessage(msg2);

        assertThat(norm1).isEqualTo("Order <NUM> failed for user <NUM> with txId <UUID> at <DATE>");
        assertThat(norm2).isEqualTo("Order <NUM> failed for user <NUM> with txId <UUID> at <DATE>");
        assertThat(norm1).isEqualTo(norm2);
    }

    @Test
    void extractsExceptionTypesCorrectly() {
        String explicit = fingerprintService.extractErrorType("com.payment.PaymentGatewayTimeoutException", null, null);
        assertThat(explicit).isEqualTo("PaymentGatewayTimeoutException");

        String fromStack = fingerprintService.extractErrorType(
                null,
                "Request failed",
                "org.postgresql.util.PSQLException: ERROR: deadlock detected\n\tat com.order.OrderRepo"
        );
        assertThat(fromStack).isEqualTo("PSQLException");

        String fromMsg = fingerprintService.extractErrorType(
                null,
                "Encountered NullPointerException while processing payload",
                null
        );
        assertThat(fromMsg).isEqualTo("NullPointerException");
    }

    @Test
    void generatesConsistentFingerprintForSimilarErrors() {
        String fp1 = fingerprintService.generateFingerprint(
                "payment-service",
                "PaymentGatewayTimeoutException",
                "Connection to Stripe timed out after <NUM>ms (txId=<UUID>)",
                "at com.payment.StripeClient.charge(StripeClient.java:142);"
        );

        String fp2 = fingerprintService.generateFingerprint(
                "payment-service",
                "PaymentGatewayTimeoutException",
                "Connection to Stripe timed out after <NUM>ms (txId=<UUID>)",
                "at com.payment.StripeClient.charge(StripeClient.java:142);"
        );

        assertThat(fp1).startsWith("fp_");
        assertThat(fp1).isEqualTo(fp2);
    }

    @Test
    void generatesDifferentFingerprintsForDifferentServicesOrErrors() {
        String fpPayment = fingerprintService.generateFingerprint(
                "payment-service", "TimeoutException", "timed out", ""
        );
        String fpOrder = fingerprintService.generateFingerprint(
                "order-service", "TimeoutException", "timed out", ""
        );
        String fpDeadlock = fingerprintService.generateFingerprint(
                "order-service", "PSQLException", "deadlock detected", ""
        );

        assertThat(fpPayment).isNotEqualTo(fpOrder);
        assertThat(fpOrder).isNotEqualTo(fpDeadlock);
    }
}
