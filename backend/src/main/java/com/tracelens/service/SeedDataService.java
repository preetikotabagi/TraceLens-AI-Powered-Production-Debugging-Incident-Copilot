package com.tracelens.service;

import com.tracelens.dto.BulkLogIngestRequest;
import com.tracelens.dto.BulkLogIngestResponse;
import com.tracelens.dto.LogEntryDto;
import com.tracelens.dto.LogLevel;
import com.tracelens.dto.SeedResponse;
import com.tracelens.repository.ErrorGroupRepository;
import com.tracelens.repository.IncidentRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Generates >= 1,000 realistic production logs across 5 interconnected services.
 * Embeds 3 realistic production incident scenarios:
 * 1. Payment Gateway Timeout (504 Gateway Timeout on Stripe webhook/charge)
 * 2. Database Deadlock during high-concurrency order placement
 * 3. JWT Signature Expiration Storm on Auth service
 */
@Service
public class SeedDataService {

    private static final Logger log = LoggerFactory.getLogger(SeedDataService.class);

    private final LogService logService;
    private final ErrorGroupRepository errorGroupRepository;
    private final IncidentRepository incidentRepository;

    public SeedDataService(LogService logService,
                           ErrorGroupRepository errorGroupRepository,
                           IncidentRepository incidentRepository) {
        this.logService = logService;
        this.errorGroupRepository = errorGroupRepository;
        this.incidentRepository = incidentRepository;
    }

    public SeedResponse seedRealisticData() {
        log.info("Generating realistic seed dataset (1,000+ logs)...");
        Instant baseTime = Instant.now().minus(2, ChronoUnit.HOURS);

        List<LogEntryDto> allLogs = new ArrayList<>(1100);

        // 1. Payment Service (260 logs): Normal flows + Scenario 1 (Stripe 504 Timeout cascade)
        generatePaymentServiceLogs(allLogs, baseTime);

        // 2. Order Service (260 logs): Normal checkout + Scenario 2 (PostgreSQL Deadlock in transactions)
        generateOrderServiceLogs(allLogs, baseTime);

        // 3. Auth Service (200 logs): Normal logins + Scenario 3 (JWT Token Expiration Storm)
        generateAuthServiceLogs(allLogs, baseTime);

        // 4. Inventory Service (180 logs): Stock allocations, catalog queries, background sync
        generateInventoryServiceLogs(allLogs, baseTime);

        // 5. Notification Service (170 logs): Order emails, SMS, webhook events
        generateNotificationServiceLogs(allLogs, baseTime);

        log.info("Total generated seed logs to ingest: {}", allLogs.size());

        // Ingest in batches through LogService so fingerprinting and incident detection run genuinely
        int batchSize = 100;
        int totalIngested = 0;
        int totalErrors = 0;
        int totalIncidents = 0;

        for (int i = 0; i < allLogs.size(); i += batchSize) {
            int end = Math.min(i + batchSize, allLogs.size());
            List<LogEntryDto> batch = allLogs.subList(i, end);
            BulkLogIngestResponse resp = logService.ingestBulk(new BulkLogIngestRequest(batch));
            totalIngested += resp.ingestedCount();
            totalErrors += resp.errorCount();
            totalIncidents += resp.createdIncidentsCount();
        }

        long errorGroupsCount = errorGroupRepository.countAll(null);
        long activeIncidentsCount = incidentRepository.countActiveIncidents();

        String message = String.format(
                "Successfully seeded %d realistic logs across 5 services with %d error groups and %d active incidents.",
                totalIngested, errorGroupsCount, activeIncidentsCount
        );

        log.info(message);
        return new SeedResponse(totalIngested, (int) errorGroupsCount, (int) activeIncidentsCount, message);
    }

    private void generatePaymentServiceLogs(List<LogEntryDto> list, Instant base) {
        String service = "payment-service";

        // Normal successful charges
        for (int i = 0; i < 220; i++) {
            Instant ts = base.plus(i * 30L + (i % 7), ChronoUnit.SECONDS);
            String reqId = "req-pay-" + UUID.randomUUID().toString().substring(0, 8);
            String traceId = "tr-pay-" + (10000 + i);
            int amount = 15 + (i % 80);

            list.add(new LogEntryDto(
                    null, ts, LogLevel.INFO, service, "production",
                    String.format("Payment authorization initiated for order #%d, amount=$%d.00", 5000 + i, amount),
                    "com.payment.PaymentProcessor", "http-nio-8081-exec-" + (i % 8),
                    reqId, "usr-" + (i % 40), traceId, "sp-" + i,
                    null, null, null, null,
                    String.format("{\"orderId\":%d,\"amount\":%d,\"currency\":\"USD\"}", 5000 + i, amount)
            ));

            if (i % 5 == 0) {
                list.add(new LogEntryDto(
                        null, ts.plusMillis(120), LogLevel.INFO, service, "production",
                        String.format("Payment captured successfully: tx_id=ch_%s", UUID.randomUUID().toString().replace("-", "").substring(0, 16)),
                        "com.payment.StripeGateway", "http-nio-8081-exec-" + (i % 8),
                        reqId, "usr-" + (i % 40), traceId, "sp-" + i + "-1",
                        null, null, null, null, "{\"gateway\":\"stripe\",\"status\":\"succeeded\"}"
                ));
            }
        }

        // Scenario 1: Stripe Gateway 504 Timeout cascade (40 logs: 15 WARN, 25 ERROR)
        Instant outageStart = base.plus(75, ChronoUnit.MINUTES);
        for (int i = 0; i < 40; i++) {
            Instant ts = outageStart.plus(i * 8L, ChronoUnit.SECONDS);
            String reqId = "req-pay-fail-" + UUID.randomUUID().toString().substring(0, 8);
            String traceId = "tr-stripe-outage-" + (20000 + i);

            if (i < 15) {
                list.add(new LogEntryDto(
                        null, ts, LogLevel.WARN, service, "production",
                        String.format("Stripe API latency warning: request took %dms (threshold: 3000ms), attempt %d/3", 3200 + i * 40, (i % 3) + 1),
                        "com.payment.StripeGateway", "http-nio-8081-exec-timeout",
                        reqId, "usr-" + (i % 10), traceId, "sp-out-" + i,
                        "GatewaySlowWarning", null, null, null, "{\"endpoint\":\"/v1/charges\",\"latencyMs\":" + (3200 + i * 40) + "}"
                ));
            } else {
                String errorMsg = String.format("PaymentGatewayTimeoutException: Stripe gateway connection timed out after 5000ms (txId=%s, orderId=%d)",
                        UUID.randomUUID(), 7000 + i);
                String stack = """
                    com.payment.exception.PaymentGatewayTimeoutException: Stripe gateway connection timed out after 5000ms
                    \tat com.payment.gateway.StripeClient.charge(StripeClient.java:142)
                    \tat com.payment.service.PaymentService.processPayment(PaymentService.java:88)
                    \tat com.payment.controller.PaymentController.checkout(PaymentController.java:54)
                    """;

                list.add(new LogEntryDto(
                        null, ts, LogLevel.ERROR, service, "production",
                        errorMsg, "com.payment.StripeClient", "http-nio-8081-exec-error",
                        reqId, "usr-" + (i % 10), traceId, "sp-out-" + i,
                        "PaymentGatewayTimeoutException", stack, null, null,
                        "{\"gateway\":\"stripe\",\"httpStatus\":504,\"retryExhausted\":true}"
                ));
            }
        }
    }

    private void generateOrderServiceLogs(List<LogEntryDto> list, Instant base) {
        String service = "order-service";

        // Normal order creation
        for (int i = 0; i < 225; i++) {
            Instant ts = base.plus(i * 28L + (i % 5), ChronoUnit.SECONDS);
            String reqId = "req-ord-" + UUID.randomUUID().toString().substring(0, 8);
            String traceId = "tr-ord-" + (30000 + i);

            list.add(new LogEntryDto(
                    null, ts, LogLevel.INFO, service, "production",
                    String.format("Order #%d created for customer %s, total items: %d", 8000 + i, "cust-" + (i % 50), (i % 4) + 1),
                    "com.order.OrderService", "http-nio-8082-exec-" + (i % 6),
                    reqId, "cust-" + (i % 50), traceId, "sp-ord-" + i,
                    null, null, null, null, String.format("{\"orderId\":%d,\"status\":\"CREATED\"}", 8000 + i)
            ));

            if (i % 10 == 0) {
                list.add(new LogEntryDto(
                        null, ts.plusMillis(80), LogLevel.DEBUG, service, "production",
                        String.format("Dispatched order.created event to Kafka topic 'order-events' partition %d", i % 3),
                        "com.order.kafka.OrderEventPublisher", "kafka-producer-1",
                        reqId, "cust-" + (i % 50), traceId, "sp-ord-k-" + i,
                        null, null, null, null, null
                ));
            }
        }

        // Scenario 2: Postgres Deadlock during flash sale (35 logs: 15 WARN, 20 ERROR)
        Instant deadlockStart = base.plus(45, ChronoUnit.MINUTES);
        for (int i = 0; i < 35; i++) {
            Instant ts = deadlockStart.plus(i * 6L, ChronoUnit.SECONDS);
            String reqId = "req-ord-lock-" + UUID.randomUUID().toString().substring(0, 8);
            String traceId = "tr-deadlock-" + (40000 + i);

            if (i < 15) {
                list.add(new LogEntryDto(
                        null, ts, LogLevel.WARN, service, "production",
                        String.format("Lock acquisition wait time high: %dms on table 'order_inventory_allocation' for sku=%s",
                                1200 + i * 50, "SKU-LIMITED-" + (i % 3)),
                        "com.order.repository.OrderRepository", "http-nio-8082-exec-db",
                        reqId, "cust-" + i, traceId, "sp-ord-lock-" + i,
                        "SlowLockAcquisitionWarning", null, null, null, "{\"table\":\"order_inventory_allocation\"}"
                ));
            } else {
                String errorMsg = String.format("PSQLException: ERROR: deadlock detected between transaction %d and %d on table orders (key=%s)",
                        91000 + i, 92000 + i, UUID.randomUUID());
                String stack = """
                    org.postgresql.util.PSQLException: ERROR: deadlock detected
                    \tDetail: Process 4123 waits for ShareLock on transaction 91234; blocked by process 4125.
                    \tat org.postgresql.core.v3.QueryExecutorImpl.receiveErrorResponse(QueryExecutorImpl.java:2711)
                    \tat com.order.repository.OrderRepository.updateOrderStatus(OrderRepository.java:142)
                    \tat com.order.service.OrderCheckoutService.processCheckout(OrderCheckoutService.java:95)
                    """;

                list.add(new LogEntryDto(
                        null, ts, LogLevel.ERROR, service, "production",
                        errorMsg, "com.order.repository.OrderRepository", "http-nio-8082-exec-error",
                        reqId, "cust-" + i, traceId, "sp-ord-deadlock-" + i,
                        "PSQLException", stack, null, null,
                        "{\"sqlState\":\"40P01\",\"transactionId\":" + (91000 + i) + "}"
                ));
            }
        }
    }

    private void generateAuthServiceLogs(List<LogEntryDto> list, Instant base) {
        String service = "auth-service";

        // Normal authentication
        for (int i = 0; i < 170; i++) {
            Instant ts = base.plus(i * 35L + (i % 4), ChronoUnit.SECONDS);
            String reqId = "req-auth-" + UUID.randomUUID().toString().substring(0, 8);
            String traceId = "tr-auth-" + (50000 + i);

            list.add(new LogEntryDto(
                    null, ts, LogLevel.INFO, service, "production",
                    String.format("User authentication successful for principal user_%d@acme.corp", 100 + (i % 45)),
                    "com.auth.security.AuthenticationManager", "http-nio-8083-exec-" + (i % 4),
                    reqId, "user_" + (100 + (i % 45)), traceId, "sp-auth-" + i,
                    null, null, null, null, "{\"grantType\":\"password\",\"mfaEnabled\":true}"
            ));
        }

        // Scenario 3: JWT Signature Expired Storm (30 logs: 10 WARN, 20 ERROR)
        Instant jwtStormStart = base.plus(100, ChronoUnit.MINUTES);
        for (int i = 0; i < 30; i++) {
            Instant ts = jwtStormStart.plus(i * 5L, ChronoUnit.SECONDS);
            String reqId = "req-jwt-storm-" + UUID.randomUUID().toString().substring(0, 8);
            String traceId = "tr-jwt-" + (60000 + i);

            if (i < 10) {
                list.add(new LogEntryDto(
                        null, ts, LogLevel.WARN, service, "production",
                        String.format("Token approaching expiration: TTL < 60s for client mobile-app-v2.1 (userId=%d)", 200 + i),
                        "com.auth.jwt.TokenValidator", "http-nio-8083-exec-jwt",
                        reqId, "usr-" + (200 + i), traceId, "sp-jwt-w-" + i,
                        "TokenExpiringWarning", null, null, null, null
                ));
            } else {
                String errorMsg = String.format("SignatureVerificationException: JWT signature expired for user session %s (tokenIssuedAt=%s)",
                        UUID.randomUUID(), base.minus(24, ChronoUnit.HOURS));
                String stack = """
                    com.auth.exception.SignatureVerificationException: JWT signature expired
                    \tat com.auth.jwt.JwtTokenParser.validateClaims(JwtTokenParser.java:82)
                    \tat com.auth.security.JwtAuthFilter.doFilterInternal(JwtAuthFilter.java:54)
                    \tat org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116)
                    """;

                list.add(new LogEntryDto(
                        null, ts, LogLevel.ERROR, service, "production",
                        errorMsg, "com.auth.jwt.JwtTokenParser", "http-nio-8083-exec-error",
                        reqId, "usr-" + (200 + i), traceId, "sp-jwt-e-" + i,
                        "SignatureVerificationException", stack, null, null,
                        "{\"claim\":\"exp\",\"clientId\":\"mobile-app-v2.1\"}"
                ));
            }
        }
    }

    private void generateInventoryServiceLogs(List<LogEntryDto> list, Instant base) {
        String service = "inventory-service";

        for (int i = 0; i < 180; i++) {
            Instant ts = base.plus(i * 38L + (i % 6), ChronoUnit.SECONDS);
            String reqId = "req-inv-" + UUID.randomUUID().toString().substring(0, 8);
            String traceId = "tr-inv-" + (70000 + i);

            if (i % 40 == 0) {
                list.add(new LogEntryDto(
                        null, ts, LogLevel.WARN, service, "production",
                        String.format("Low stock warning: SKU-ITEM-%d inventory below safety threshold (remaining: %d)", 100 + i, (i % 5)),
                        "com.inventory.service.StockMonitor", "scheduled-stock-worker-1",
                        reqId, null, traceId, "sp-inv-w-" + i,
                        "LowStockWarning", null, null, null, "{\"sku\":\"SKU-ITEM-" + (100 + i) + "\"}"
                ));
            } else {
                list.add(new LogEntryDto(
                        null, ts, LogLevel.INFO, service, "production",
                        String.format("Stock reserved for SKU-ITEM-%d, quantity=%d, warehouse=wh-us-east-1", (100 + i), (i % 3) + 1),
                        "com.inventory.service.InventoryReservationService", "http-nio-8084-exec-" + (i % 4),
                        reqId, null, traceId, "sp-inv-" + i,
                        null, null, null, null, "{\"warehouse\":\"wh-us-east-1\"}"
                ));
            }
        }
    }

    private void generateNotificationServiceLogs(List<LogEntryDto> list, Instant base) {
        String service = "notification-service";

        for (int i = 0; i < 170; i++) {
            Instant ts = base.plus(i * 40L + (i % 3), ChronoUnit.SECONDS);
            String reqId = "req-notif-" + UUID.randomUUID().toString().substring(0, 8);
            String traceId = "tr-notif-" + (80000 + i);

            if (i % 30 == 0) {
                list.add(new LogEntryDto(
                        null, ts, LogLevel.WARN, service, "production",
                        String.format("SES email rate limit threshold reached: 85%% of quota consumed for recipient domain @example.com"),
                        "com.notification.email.SesEmailSender", "email-dispatcher-1",
                        reqId, null, traceId, "sp-notif-w-" + i,
                        "EmailRateLimitWarning", null, null, null, null
                ));
            } else {
                list.add(new LogEntryDto(
                        null, ts, LogLevel.INFO, service, "production",
                        String.format("Dispatch notification template '%s' delivered successfully via AWS SES to customer %s",
                                i % 2 == 0 ? "order_confirmation" : "shipping_update", "cust-" + (i % 40)),
                        "com.notification.email.SesEmailSender", "email-dispatcher-1",
                        reqId, "cust-" + (i % 40), traceId, "sp-notif-" + i,
                        null, null, null, null, "{\"channel\":\"EMAIL\",\"provider\":\"AWS_SES\"}"
                ));
            }
        }
    }
}
