package com.tracelens.service;

import com.tracelens.dto.AiInvestigationRequest;
import com.tracelens.dto.AiInvestigationResponse;
import com.tracelens.dto.IncidentResponse;
import com.tracelens.dto.IncidentStatus;
import com.tracelens.dto.LogEntryDto;
import com.tracelens.dto.RecommendationDto;
import com.tracelens.dto.RootCauseHypothesisDto;
import com.tracelens.dto.SuspiciousEventDto;
import com.tracelens.exception.ResourceNotFoundException;
import com.tracelens.repository.IncidentRepository;
import com.tracelens.repository.InvestigationRepository;
import com.tracelens.repository.LogRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LlmInvestigationService {

    private static final Logger log = LoggerFactory.getLogger(LlmInvestigationService.class);

    private final IncidentRepository incidentRepository;
    private final LogRepository logRepository;
    private final InvestigationRepository investigationRepository;
    private final LlmClient llmClient;

    public LlmInvestigationService(IncidentRepository incidentRepository,
                                   LogRepository logRepository,
                                   InvestigationRepository investigationRepository,
                                   LlmClient llmClient) {
        this.incidentRepository = incidentRepository;
        this.logRepository = logRepository;
        this.investigationRepository = investigationRepository;
        this.llmClient = llmClient;
    }

    @Transactional
    public AiInvestigationResponse investigateIncident(Long incidentId, AiInvestigationRequest request) {
        IncidentResponse incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with ID: " + incidentId));

        // 1. Gather associated and surrounding context logs
        List<LogEntryDto> contextLogs = logRepository.findByIncidentId(incidentId, 25);
        if (contextLogs.isEmpty() && incident.fingerprint() != null) {
            contextLogs = logRepository.findByFingerprint(incident.fingerprint(), 25);
        }
        if (contextLogs.isEmpty() && incident.lastSeen() != null) {
            contextLogs = logRepository.findLogsAroundTime(incident.serviceName(), incident.lastSeen(), 5, 25);
        }

        // 2. If a real LLM API key is present, invoke genuine LLM API with fallback handling
        AiInvestigationResponse generated;
        if (llmClient.isConfigured()) {
            try {
                String systemPrompt = buildSystemPrompt();
                String userPrompt = buildUserPrompt(incident, contextLogs, request);
                generated = llmClient.generateInvestigation(incidentId, systemPrompt, userPrompt);
            } catch (Exception ex) {
                log.warn("Live LLM investigation failed for incident #{}: {}. Engaging deterministic rule-based fallback.",
                        incidentId, ex.getMessage());
                generated = generateHeuristicInvestigation(incident, contextLogs,
                        "Rule-Based Fallback Engine (LLM temporarily unavailable: " + ex.getMessage() + ")");
            }
        } else {
            log.info("LLM API key not configured. Generating deterministic heuristic investigation for incident #{}", incidentId);
            generated = generateHeuristicInvestigation(incident, contextLogs, "Rule-Based Engine (API key not configured)");
        }

        // 3. Persist investigation result to database
        AiInvestigationResponse saved = investigationRepository.save(
                incidentId,
                generated.model(),
                generated.summary(),
                generated.suspiciousSequence(),
                generated.hypotheses(),
                generated.recommendations(),
                generated.rawResponse()
        );

        // 4. Update incident status to INVESTIGATING if currently OPEN
        if (incident.status() == IncidentStatus.OPEN) {
            incidentRepository.updateStatus(incidentId, IncidentStatus.INVESTIGATING);
        }

        return saved;
    }

    public List<AiInvestigationResponse> getInvestigationsForIncident(Long incidentId) {
        incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with ID: " + incidentId));

        return investigationRepository.findByIncidentId(incidentId);
    }

    private String buildSystemPrompt() {
        return """
            You are TraceLens, an expert SRE and incident debugging copilot.
            Analyze the provided production incident details and supporting log sequence.
            Distinguish strictly between OBSERVED LOG FACTS and INFERRED HYPOTHESES.
            Never present assumptions or guesses as confirmed root causes.

            Return a STRICT JSON object with the following schema:
            {
              "summary": "Brief 1-2 sentence incident overview.",
              "suspiciousSequence": [
                {
                  "service": "service-name",
                  "event": "Description of suspicious event",
                  "suspicionReason": "Why this log event is anomalous"
                }
              ],
              "rootCauseHypotheses": [
                {
                  "hypothesis": "Plausible root cause hypothesis",
                  "confidence": "HIGH | MEDIUM | LOW",
                  "supportingEvidence": ["Direct log quote or observed pattern"],
                  "reasoning": "Deductive chain connecting observed logs to this hypothesis"
                }
              ],
              "recommendations": [
                {
                  "action": "Concrete actionable remediation step",
                  "priority": "IMMEDIATE | SHORT_TERM | LONG_TERM",
                  "rationale": "Why this mitigation prevents recurrence"
                }
              ]
            }
            """;
    }

    private String buildUserPrompt(IncidentResponse incident, List<LogEntryDto> contextLogs, AiInvestigationRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("INCIDENT DETAILS:\n");
        sb.append("ID: ").append(incident.id()).append("\n");
        sb.append("Service: ").append(incident.serviceName()).append("\n");
        sb.append("Severity: ").append(incident.severity()).append("\n");
        sb.append("Title: ").append(incident.title()).append("\n");
        sb.append("Trigger Rule: ").append(incident.triggerRule()).append("\n");
        sb.append("Occurrences: ").append(incident.occurrenceCount()).append("\n");
        sb.append("First Seen: ").append(incident.firstSeen()).append("\n");
        sb.append("Last Seen: ").append(incident.lastSeen()).append("\n");
        sb.append("Summary: ").append(incident.summary()).append("\n");

        if (request != null && request.notes() != null && !request.notes().isBlank()) {
            sb.append("Investigator Notes: ").append(request.notes().trim()).append("\n");
        }

        sb.append("\nSUPPORTING LOG SEQUENCE (up to 25 records):\n");
        if (contextLogs.isEmpty()) {
            sb.append("(No logs directly associated yet; incident generated from rule threshold)\n");
        } else {
            for (LogEntryDto logEntry : contextLogs) {
                sb.append(String.format("[%s] [%s] [%s] %s",
                        logEntry.timestamp(), logEntry.level(), logEntry.serviceName(), logEntry.message()));
                if (logEntry.requestId() != null && !logEntry.requestId().isBlank()) {
                    sb.append(" [ReqId: ").append(logEntry.requestId()).append("]");
                }
                if (logEntry.errorType() != null && !logEntry.errorType().isBlank()) {
                    sb.append(" [ErrorType: ").append(logEntry.errorType()).append("]");
                }
                if (logEntry.stackTrace() != null && !logEntry.stackTrace().isBlank()) {
                    String snippet = logEntry.stackTrace().lines().findFirst().orElse("");
                    sb.append(" [Trace: ").append(snippet).append("]");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private AiInvestigationResponse generateHeuristicInvestigation(IncidentResponse incident,
                                                                   List<LogEntryDto> contextLogs,
                                                                   String fallbackReason) {
        String service = incident.serviceName();
        String title = incident.title();

        List<SuspiciousEventDto> sequence = new ArrayList<>();
        List<String> evidence = new ArrayList<>();

        if (contextLogs.isEmpty()) {
            sequence.add(new SuspiciousEventDto(
                    incident.lastSeen() != null ? incident.lastSeen() : Instant.now(),
                    service,
                    "High error rate trigger exceeded threshold",
                    "Service exceeded configurable error threshold within sliding window"
            ));
            evidence.add("Incident rule triggered: " + incident.triggerRule());
        } else {
            for (LogEntryDto l : contextLogs) {
                if (l.level() == com.tracelens.dto.LogLevel.ERROR || l.level() == com.tracelens.dto.LogLevel.FATAL) {
                    sequence.add(new SuspiciousEventDto(
                            l.timestamp() != null ? l.timestamp() : Instant.now(),
                            l.serviceName(),
                            (l.requestId() != null ? "[" + l.requestId() + "] " : "") + l.message(),
                            "Explicit failure with errorType=" + (l.errorType() != null ? l.errorType() : "Unknown")
                    ));
                    if (evidence.size() < 6) {
                        evidence.add(String.format("[%s] [%s] %s: %s",
                                l.timestamp(),
                                l.requestId() != null ? l.requestId() : "no-req",
                                l.serviceName(),
                                l.message()));
                    }
                }
            }
        }

        List<RootCauseHypothesisDto> hypotheses = new ArrayList<>();
        List<RecommendationDto> recommendations = new ArrayList<>();

        if (title.toLowerCase().contains("timeout") || title.toLowerCase().contains("gateway") || service.contains("payment")) {
            hypotheses.add(new RootCauseHypothesisDto(
                    "Downstream Payment Provider Network Latency or 504 Gateway Timeout",
                    "HIGH",
                    evidence,
                    "Observed repeated timeouts while invoking external payment gateway within the configured HTTP client deadline."
            ));
            recommendations.add(new RecommendationDto(
                    "Enable circuit breaker with exponential backoff on payment gateway calls",
                    "IMMEDIATE",
                    "Prevents request thread pool exhaustion when downstream processor is degraded."
            ));
            recommendations.add(new RecommendationDto(
                    "Review external gateway status page and switch to secondary fallback provider",
                    "SHORT_TERM",
                    "Ensures order checkout continuity during 3rd-party merchant gateway outages."
            ));
        } else if (title.toLowerCase().contains("deadlock") || title.toLowerCase().contains("psql") || service.contains("order")) {
            hypotheses.add(new RootCauseHypothesisDto(
                    "Concurrent Database Transaction Lock Contention and Deadlock",
                    "HIGH",
                    evidence,
                    "Concurrent transactions updating orders and inventory rows acquired row locks in conflicting order during checkout spike."
            ));
            recommendations.add(new RecommendationDto(
                    "Add optimistic locking or consistent lock acquisition ordering across transactions",
                    "IMMEDIATE",
                    "Guarantees all database transactions lock records in the same key sequence, eliminating deadlocks."
            ));
            recommendations.add(new RecommendationDto(
                    "Tune Postgres deadlock_timeout and connection pool limits",
                    "SHORT_TERM",
                    "Fails fast on deadlocks and retries transaction with jitter."
            ));
        } else if (title.toLowerCase().contains("jwt") || title.toLowerCase().contains("auth") || title.toLowerCase().contains("token") || title.toLowerCase().contains("signature")) {
            hypotheses.add(new RootCauseHypothesisDto(
                    "JWT Token Expiration Storm and Signing Key Mismatch",
                    "HIGH",
                    evidence,
                    "Sudden surge of expired or improperly refreshed tokens rejected by authentication filter."
            ));
            recommendations.add(new RecommendationDto(
                    "Verify token refresh rotation flow on client applications",
                    "IMMEDIATE",
                    "Clients should preemptively refresh tokens prior to TTL expiry."
            ));
            recommendations.add(new RecommendationDto(
                    "Review clock synchronization (NTP) across authentication service pods",
                    "SHORT_TERM",
                    "Eliminates token rejection due to pod clock drift."
            ));
        } else {
            hypotheses.add(new RootCauseHypothesisDto(
                    "Uncaught Exception in Service Processing Loop",
                    "MEDIUM",
                    evidence,
                    "Recurring errors indicate unhandled edge case in payload processing."
            ));
            recommendations.add(new RecommendationDto(
                    "Add defensive null checks and circuit breaking",
                    "IMMEDIATE",
                    "Isolate failure domain to prevent cascading outages."
            ));
        }

        String summary = String.format(
                "[Deterministic Fallback Analysis] Incident #%d on service '%s' experienced %d occurrences of recurring failures. "
                        + "Identified root cause: %s. Supporting evidence gathered from %d context log events.",
                incident.id(),
                service,
                incident.occurrenceCount(),
                hypotheses.get(0).hypothesis(),
                contextLogs.size()
        );

        return new AiInvestigationResponse(
                null,
                incident.id(),
                fallbackReason != null ? fallbackReason : "Rule-Based Fallback Engine",
                summary,
                sequence,
                hypotheses,
                recommendations,
                "{\"engine\":\"rule_based_fallback\",\"reason\":\"" + (fallbackReason != null ? fallbackReason : "fallback") + "\",\"evidenceCount\":" + evidence.size() + "}",
                Instant.now()
        );
    }
}
