package com.tracelens.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tracelens.config.TraceLensProperties;
import com.tracelens.dto.IncidentResponse;
import com.tracelens.dto.IncidentStatus;
import com.tracelens.dto.LogEntryDto;
import com.tracelens.dto.LogLevel;
import com.tracelens.dto.Severity;
import com.tracelens.repository.IncidentRepository;
import com.tracelens.repository.LogRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IncidentDetectionServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private LogRepository logRepository;

    private IncidentDetectionService detectionService;

    @BeforeEach
    void setUp() {
        TraceLensProperties properties = new TraceLensProperties(
                new TraceLensProperties.App("TraceLens", "0.1.0"),
                new TraceLensProperties.Demo(false, 10000),
                new TraceLensProperties.Cors(List.of()),
                new TraceLensProperties.Llm(false, "", "gpt-4o-mini", "", "openai", 10000),
                new TraceLensProperties.IncidentRules(5, 5, 1, 3)
        );
        detectionService = new IncidentDetectionService(incidentRepository, logRepository, properties);
    }

    @Test
    void ignoresInfoAndDebugLogs() {
        LogEntryDto infoLog = new LogEntryDto(
                1L, Instant.now(), LogLevel.INFO, "order-service", "prod",
                "Order created", null, null, null, null, null, null, null, null, null, null, null
        );

        Optional<IncidentResponse> incident = detectionService.evaluateLog(infoLog, 0);
        assertThat(incident).isEmpty();
    }

    @Test
    void triggersCriticalIncidentOnDatabaseDeadlock() {
        LogEntryDto deadlockLog = new LogEntryDto(
                2L, Instant.now(), LogLevel.ERROR, "order-service", "prod",
                "PSQLException: ERROR: deadlock detected between transaction 12 and 14",
                null, null, null, null, null, null,
                "PSQLException", "stack trace", "fp_deadlock123", null, null
        );

        IncidentResponse mockCreated = new IncidentResponse(
                10L, "[CRITICAL] PSQLException on order-service", "order-service",
                Severity.CRITICAL, IncidentStatus.OPEN, "fp_deadlock123",
                "CRITICAL_ERROR_SIGNATURE", 1, Instant.now(), Instant.now(),
                "Summary", Instant.now(), Instant.now()
        );

        when(incidentRepository.findActiveByFingerprint("fp_deadlock123")).thenReturn(Optional.empty());
        when(incidentRepository.findActiveByService("order-service")).thenReturn(Optional.empty());
        when(incidentRepository.create(any(), eq("order-service"), eq(Severity.CRITICAL), eq(IncidentStatus.OPEN),
                eq("fp_deadlock123"), eq("CRITICAL_ERROR_SIGNATURE"), eq(1), any(), any(), any()))
                .thenReturn(mockCreated);

        Optional<IncidentResponse> result = detectionService.evaluateLog(deadlockLog, 1);

        assertThat(result).isPresent();
        assertThat(result.get().severity()).isEqualTo(Severity.CRITICAL);
        assertThat(result.get().triggerRule()).isEqualTo("CRITICAL_ERROR_SIGNATURE");
    }

    @Test
    void triggersHighSeverityOnFrequencySpike() {
        LogEntryDto errorLog = new LogEntryDto(
                3L, Instant.now(), LogLevel.ERROR, "payment-service", "prod",
                "Gateway failed to respond", null, null, null, null, null, null,
                "GatewayException", null, "fp_spike123", null, null
        );

        when(logRepository.countErrorsForServiceInWindow(eq("payment-service"), any())).thenReturn(6);
        when(incidentRepository.findActiveByFingerprint("fp_spike123")).thenReturn(Optional.empty());
        when(incidentRepository.findActiveByService("payment-service")).thenReturn(Optional.empty());

        IncidentResponse mockCreated = new IncidentResponse(
                11L, "[HIGH] GatewayException on payment-service", "payment-service",
                Severity.HIGH, IncidentStatus.OPEN, "fp_spike123",
                "HIGH_ERROR_FREQUENCY_SPIKE", 1, Instant.now(), Instant.now(),
                "Summary", Instant.now(), Instant.now()
        );

        when(incidentRepository.create(any(), eq("payment-service"), eq(Severity.HIGH), eq(IncidentStatus.OPEN),
                eq("fp_spike123"), eq("HIGH_ERROR_FREQUENCY_SPIKE"), eq(1), any(), any(), any()))
                .thenReturn(mockCreated);

        Optional<IncidentResponse> result = detectionService.evaluateLog(errorLog, 1);

        assertThat(result).isPresent();
        assertThat(result.get().severity()).isEqualTo(Severity.HIGH);
    }

    @Test
    void escalatesExistingActiveIncidentWhenFingerprintMatches() {
        LogEntryDto errorLog = new LogEntryDto(
                4L, Instant.now(), LogLevel.ERROR, "payment-service", "prod",
                "Gateway failed again", null, null, null, null, null, null,
                "GatewayException", null, "fp_existing", null, null
        );

        IncidentResponse existing = new IncidentResponse(
                15L, "[MEDIUM] GatewayException", "payment-service",
                Severity.MEDIUM, IncidentStatus.OPEN, "fp_existing",
                "REPEATED_ERROR_FINGERPRINT", 4, Instant.now(), Instant.now(),
                "Summary", Instant.now(), Instant.now()
        );

        when(incidentRepository.findActiveByFingerprint("fp_existing")).thenReturn(Optional.of(existing));
        when(incidentRepository.findById(15L)).thenReturn(Optional.of(existing));

        Optional<IncidentResponse> result = detectionService.evaluateLog(errorLog, 5);

        assertThat(result).isPresent();
        verify(incidentRepository).incrementOccurrence(eq(15L), eq(1), any(), any());
    }
}
