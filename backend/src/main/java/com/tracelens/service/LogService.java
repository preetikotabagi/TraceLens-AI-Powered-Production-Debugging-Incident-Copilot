package com.tracelens.service;

import com.tracelens.dto.BulkLogIngestRequest;
import com.tracelens.dto.BulkLogIngestResponse;
import com.tracelens.dto.ErrorGroupResponse;
import com.tracelens.dto.IncidentResponse;
import com.tracelens.dto.LogEntryDto;
import com.tracelens.dto.LogLevel;
import com.tracelens.dto.PagedResponse;
import com.tracelens.repository.ErrorGroupRepository;
import com.tracelens.repository.LogRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogService {

    private final LogRepository logRepository;
    private final ErrorGroupRepository errorGroupRepository;
    private final ErrorFingerprintService fingerprintService;
    private final IncidentDetectionService incidentDetectionService;

    public LogService(LogRepository logRepository,
                      ErrorGroupRepository errorGroupRepository,
                      ErrorFingerprintService fingerprintService,
                      IncidentDetectionService incidentDetectionService) {
        this.logRepository = logRepository;
        this.errorGroupRepository = errorGroupRepository;
        this.fingerprintService = fingerprintService;
        this.incidentDetectionService = incidentDetectionService;
    }

    /**
     * Ingests a single log entry, running error fingerprinting and incident detection if needed.
     */
    @Transactional
    public LogEntryDto ingest(LogEntryDto incoming) {
        Instant ts = incoming.timestamp() != null ? incoming.timestamp() : Instant.now();
        String fingerprint = incoming.fingerprint();
        String errorType = incoming.errorType();
        Long incidentId = incoming.incidentId();

        if (incoming.level() == LogLevel.ERROR || incoming.level() == LogLevel.FATAL) {
            String template = fingerprintService.normalizeMessage(incoming.message());
            errorType = fingerprintService.extractErrorType(incoming.errorType(), incoming.message(), incoming.stackTrace());
            String topFrames = fingerprintService.extractTopFrames(incoming.stackTrace());
            fingerprint = fingerprintService.generateFingerprint(incoming.serviceName(), errorType, template, topFrames);

            ErrorGroupResponse eg = errorGroupRepository.upsert(
                    fingerprint, errorType, incoming.serviceName(), template, incoming.stackTrace(), ts
            );

            LogEntryDto processedForEvaluation = new LogEntryDto(
                    incoming.id(), ts, incoming.level(), incoming.serviceName(), incoming.environment(),
                    incoming.message(), incoming.logger(), incoming.threadName(), incoming.requestId(),
                    incoming.userId(), incoming.traceId(), incoming.spanId(), errorType,
                    incoming.stackTrace(), fingerprint, incidentId, incoming.metadata()
            );

            Optional<IncidentResponse> incident = incidentDetectionService.evaluateLog(
                    processedForEvaluation, eg.occurrenceCount()
            );
            if (incident.isPresent()) {
                incidentId = incident.get().id();
            }
        }

        LogEntryDto toSave = new LogEntryDto(
                incoming.id(), ts, incoming.level(), incoming.serviceName(), incoming.environment(),
                incoming.message(), incoming.logger(), incoming.threadName(), incoming.requestId(),
                incoming.userId(), incoming.traceId(), incoming.spanId(), errorType,
                incoming.stackTrace(), fingerprint, incidentId, incoming.metadata()
        );

        return logRepository.save(toSave);
    }

    /**
     * Ingests a batch of logs efficiently.
     */
    @Transactional
    public BulkLogIngestResponse ingestBulk(BulkLogIngestRequest request) {
        if (request == null || request.logs() == null || request.logs().isEmpty()) {
            return new BulkLogIngestResponse(0, 0, 0, "No logs provided.");
        }

        int errorCount = 0;
        int createdIncidents = 0;
        List<LogEntryDto> prepared = new ArrayList<>();

        for (LogEntryDto incoming : request.logs()) {
            Instant ts = incoming.timestamp() != null ? incoming.timestamp() : Instant.now();
            String fingerprint = incoming.fingerprint();
            String errorType = incoming.errorType();
            Long incidentId = incoming.incidentId();

            if (incoming.level() == LogLevel.ERROR || incoming.level() == LogLevel.FATAL) {
                errorCount++;
                String template = fingerprintService.normalizeMessage(incoming.message());
                errorType = fingerprintService.extractErrorType(incoming.errorType(), incoming.message(), incoming.stackTrace());
                String topFrames = fingerprintService.extractTopFrames(incoming.stackTrace());
                fingerprint = fingerprintService.generateFingerprint(incoming.serviceName(), errorType, template, topFrames);

                ErrorGroupResponse eg = errorGroupRepository.upsert(
                        fingerprint, errorType, incoming.serviceName(), template, incoming.stackTrace(), ts
                );

                LogEntryDto processed = new LogEntryDto(
                        incoming.id(), ts, incoming.level(), incoming.serviceName(), incoming.environment(),
                        incoming.message(), incoming.logger(), incoming.threadName(), incoming.requestId(),
                        incoming.userId(), incoming.traceId(), incoming.spanId(), errorType,
                        incoming.stackTrace(), fingerprint, incidentId, incoming.metadata()
                );

                Optional<IncidentResponse> incident = incidentDetectionService.evaluateLog(
                        processed, eg.occurrenceCount()
                );
                if (incident.isPresent()) {
                    incidentId = incident.get().id();
                    createdIncidents++;

                    final Long matchedIncidentId = incidentId;
                    final String matchedFp = fingerprint;
                    for (int p = 0; p < prepared.size(); p++) {
                        LogEntryDto prev = prepared.get(p);
                        if (matchedFp != null && matchedFp.equals(prev.fingerprint()) && prev.incidentId() == null) {
                            prepared.set(p, new LogEntryDto(
                                    prev.id(), prev.timestamp(), prev.level(), prev.serviceName(), prev.environment(),
                                    prev.message(), prev.logger(), prev.threadName(), prev.requestId(),
                                    prev.userId(), prev.traceId(), prev.spanId(), prev.errorType(),
                                    prev.stackTrace(), prev.fingerprint(), matchedIncidentId, prev.metadata()
                            ));
                        }
                    }
                }
            }

            prepared.add(new LogEntryDto(
                    incoming.id(), ts, incoming.level(), incoming.serviceName(), incoming.environment(),
                    incoming.message(), incoming.logger(), incoming.threadName(), incoming.requestId(),
                    incoming.userId(), incoming.traceId(), incoming.spanId(), errorType,
                    incoming.stackTrace(), fingerprint, incidentId, incoming.metadata()
            ));
        }

        logRepository.saveAll(prepared);

        return new BulkLogIngestResponse(
                prepared.size(),
                errorCount,
                createdIncidents,
                String.format("Successfully ingested %d log records.", prepared.size())
        );
    }

    public PagedResponse<LogEntryDto> search(String serviceName, LogLevel level, String query,
                                            String fingerprint, Long incidentId,
                                            Instant startTime, Instant endTime,
                                            int limit, int offset) {
        int boundedLimit = Math.max(1, Math.min(limit, 200));
        int boundedOffset = Math.max(0, offset);

        List<LogEntryDto> items = logRepository.search(
                serviceName, level, query, fingerprint, incidentId, startTime, endTime, boundedLimit, boundedOffset
        );
        long total = logRepository.count(
                serviceName, level, query, fingerprint, incidentId, startTime, endTime
        );

        boolean hasMore = (boundedOffset + items.size()) < total;
        return new PagedResponse<>(items, total, boundedLimit, boundedOffset, hasMore);
    }

    public Optional<LogEntryDto> findById(Long id) {
        return logRepository.findById(id);
    }
}
