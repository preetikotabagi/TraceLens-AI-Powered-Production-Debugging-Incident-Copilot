package com.tracelens.service;

import com.tracelens.config.TraceLensProperties;
import com.tracelens.dto.IncidentResponse;
import com.tracelens.dto.IncidentStatus;
import com.tracelens.dto.LogEntryDto;
import com.tracelens.dto.LogLevel;
import com.tracelens.dto.Severity;
import com.tracelens.repository.IncidentRepository;
import com.tracelens.repository.LogRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rule-based incident detection engine.
 * Evaluates incoming error logs against sliding time window thresholds,
 * recurring failure fingerprints, and critical error signatures.
 */
@Service
public class IncidentDetectionService {

    private static final Logger log = LoggerFactory.getLogger(IncidentDetectionService.class);

    private static final Set<String> CRITICAL_ERROR_SUBSTRINGS = Set.of(
            "deadlock", "databaseconnection", "outofmemory", "connection pool",
            "connection refused", "504 gateway timeout", "psqlexception", "dataaccessresourcefailure"
    );

    private final IncidentRepository incidentRepository;
    private final LogRepository logRepository;
    private final TraceLensProperties properties;

    public IncidentDetectionService(IncidentRepository incidentRepository,
                                    LogRepository logRepository,
                                    TraceLensProperties properties) {
        this.incidentRepository = incidentRepository;
        this.logRepository = logRepository;
        this.properties = properties;
    }

    /**
     * Evaluates an ingested log entry for incident creation or escalation.
     * Returns the associated IncidentResponse if an incident was created or updated, or empty.
     */
    @Transactional
    public Optional<IncidentResponse> evaluateLog(LogEntryDto logEntry, int currentFingerprintCount) {
        if (logEntry.level() != LogLevel.ERROR && logEntry.level() != LogLevel.FATAL) {
            return Optional.empty();
        }

        String serviceName = logEntry.serviceName();
        String fingerprint = logEntry.fingerprint();
        Instant now = logEntry.timestamp() != null ? logEntry.timestamp() : Instant.now();

        // 1. Check if error qualifies as CRITICAL rule
        boolean isCritical = isCriticalError(logEntry);

        // 2. Count errors for this service in the configured sliding time window
        int windowMinutes = properties.rules().errorWindowMinutes();
        Instant windowStart = now.minusSeconds(windowMinutes * 60L);
        int recentServiceErrors = logRepository.countErrorsForServiceInWindow(serviceName, windowStart);

        // 3. Determine severity and trigger rule
        Severity severity;
        String triggerRule;
        boolean shouldTrigger = false;

        if (isCritical) {
            severity = Severity.CRITICAL;
            triggerRule = "CRITICAL_ERROR_SIGNATURE";
            shouldTrigger = true;
        } else if (recentServiceErrors >= properties.rules().errorThreshold()) {
            severity = recentServiceErrors >= (properties.rules().errorThreshold() * 2) ? Severity.CRITICAL : Severity.HIGH;
            triggerRule = "HIGH_ERROR_FREQUENCY_SPIKE";
            shouldTrigger = true;
        } else if (currentFingerprintCount >= properties.rules().recurrenceThreshold()) {
            severity = Severity.MEDIUM;
            triggerRule = "REPEATED_ERROR_FINGERPRINT";
            shouldTrigger = true;
        } else if (logEntry.level() == LogLevel.FATAL) {
            severity = Severity.HIGH;
            triggerRule = "FATAL_LOG_LEVEL";
            shouldTrigger = true;
        } else {
            severity = Severity.LOW;
            triggerRule = "ISOLATED_FAILURE";
        }

        if (!shouldTrigger) {
            return Optional.empty();
        }

        // 4. Check for active existing incident matching this fingerprint
        Optional<IncidentResponse> existingActive = Optional.empty();
        if (fingerprint != null) {
            existingActive = incidentRepository.findActiveByFingerprint(fingerprint);
        }
        if (existingActive.isEmpty()) {
            existingActive = incidentRepository.findActiveByService(serviceName);
        }

        if (existingActive.isPresent()) {
            IncidentResponse existing = existingActive.get();
            incidentRepository.incrementOccurrence(existing.id(), 1, now, severity);
            if (fingerprint != null) {
                logRepository.linkIncidentByFingerprintSince(fingerprint, windowStart, existing.id());
            }
            log.info("Escalated existing incident #{} [{}] on service {}: occurrences={}",
                    existing.id(), existing.title(), serviceName, existing.occurrenceCount() + 1);
            return incidentRepository.findById(existing.id());
        }

        // 5. Create new incident
        String title = String.format("%s on %s",
                logEntry.errorType() != null ? logEntry.errorType() : "Service Error",
                serviceName);

        String summary = String.format(
                "Triggered by rule '%s'. Detected %s in service '%s'. Message sample: %s",
                triggerRule,
                logEntry.errorType() != null ? logEntry.errorType() : "failures",
                serviceName,
                logEntry.message().length() > 200 ? logEntry.message().substring(0, 200) + "..." : logEntry.message()
        );

        int initialCount = Math.max(1, currentFingerprintCount);

        IncidentResponse created = incidentRepository.create(
                title,
                serviceName,
                severity,
                IncidentStatus.OPEN,
                fingerprint,
                triggerRule,
                initialCount,
                now,
                now,
                summary
        );

        if (fingerprint != null) {
            logRepository.linkIncidentByFingerprintSince(fingerprint, windowStart, created.id());
        }

        log.info("Detected new incident #{} [{}] on service {} with severity {}",
                created.id(), created.title(), serviceName, severity);

        return Optional.of(created);
    }

    private boolean isCriticalError(LogEntryDto logEntry) {
        String msg = (logEntry.message() != null ? logEntry.message() : "").toLowerCase();
        String errorType = (logEntry.errorType() != null ? logEntry.errorType() : "").toLowerCase();
        String stack = (logEntry.stackTrace() != null ? logEntry.stackTrace() : "").toLowerCase();

        for (String sub : CRITICAL_ERROR_SUBSTRINGS) {
            if (msg.contains(sub) || errorType.contains(sub) || stack.contains(sub)) {
                return true;
            }
        }
        return false;
    }
}
