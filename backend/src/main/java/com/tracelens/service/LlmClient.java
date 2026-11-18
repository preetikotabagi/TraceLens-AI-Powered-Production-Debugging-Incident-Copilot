package com.tracelens.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracelens.config.TraceLensProperties;
import com.tracelens.dto.AiInvestigationResponse;
import com.tracelens.dto.RecommendationDto;
import com.tracelens.dto.RootCauseHypothesisDto;
import com.tracelens.dto.SuspiciousEventDto;
import com.tracelens.exception.ApiException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Robust HTTP client for calling LLM APIs (Google Gemini and OpenAI-compatible).
 * Implements exponential backoff, transient error retries, and multi-model fallback.
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private static final List<String> GEMINI_CANDIDATE_MODELS = List.of(
            "gemini-3.5-flash-lite",
            "gemini-3-flash-preview",
            "gemini-flash-lite-latest"
    );

    private final TraceLensProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public LlmClient(TraceLensProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isConfigured() {
        String key = properties.llm().apiKey();
        return key != null && !key.isBlank() && !key.contains("change_me");
    }

    /**
     * Executes a prompt against the configured LLM API with resilience and fallback.
     */
    public AiInvestigationResponse generateInvestigation(Long incidentId, String systemPrompt, String userPrompt) {
        String apiKey = properties.llm().apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "LLM_KEY_MISSING",
                    "LLM API key is not configured. Please supply LLM_API_KEY, OPENAI_API_KEY, or GEMINI_API_KEY.");
        }

        String provider = properties.llm().provider();
        String model = properties.llm().model();

        if ("gemini".equalsIgnoreCase(provider) || apiKey.startsWith("AIza") || apiKey.startsWith("AQ.")) {
            return callGeminiWithFallback(incidentId, model, apiKey, systemPrompt, userPrompt);
        } else {
            return callOpenAiWithRetry(incidentId, model, apiKey, systemPrompt, userPrompt);
        }
    }

    private AiInvestigationResponse callGeminiWithFallback(Long incidentId, String preferredModel, String apiKey,
                                                           String systemPrompt, String userPrompt) {
        Set<String> modelQueue = new LinkedHashSet<>();
        if (preferredModel != null && preferredModel.startsWith("gemini")) {
            modelQueue.add(preferredModel);
        }
        modelQueue.addAll(GEMINI_CANDIDATE_MODELS);

        Exception lastException = null;

        for (String modelToTry : modelQueue) {
            log.info("Attempting LLM investigation for incident #{} using model '{}'", incidentId, modelToTry);
            int maxRetries = 2;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    return executeGeminiCall(incidentId, modelToTry, apiKey, systemPrompt, userPrompt);
                } catch (ApiException ae) {
                    lastException = ae;
                    int statusCode = ae.getStatus().value();
                    if (statusCode == 401 || statusCode == 403 || statusCode == 400 || statusCode == 404) {
                        log.warn("Gemini model '{}' returned non-retryable status {}: {}", modelToTry, statusCode, ae.getMessage());
                        break;
                    }
                    if (statusCode == 503 || statusCode == 429) {
                        log.warn("Gemini model '{}' returned transient status {} on attempt {}/{}. Backing off...",
                                modelToTry, statusCode, attempt, maxRetries);
                        sleepWithJitter(attempt);
                    } else {
                        break;
                    }
                } catch (Exception ex) {
                    lastException = ex;
                    log.warn("Gemini call to '{}' failed with exception on attempt {}/{}: {}",
                            modelToTry, attempt, maxRetries, ex.getMessage());
                    sleepWithJitter(attempt);
                }
            }
            log.warn("Model '{}' exhausted retries. Trying next candidate model if available...", modelToTry);
        }

        String errorDetail = lastException != null ? lastException.getMessage() : "All Gemini model candidates unavailable.";
        throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "LLM_UNAVAILABLE",
                "Gemini API temporarily unavailable across all tested models: " + errorDetail);
    }

    private AiInvestigationResponse executeGeminiCall(Long incidentId, String model, String apiKey,
                                                      String systemPrompt, String userPrompt) throws Exception {
        String url = String.format(
                "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                model,
                apiKey);

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", systemPrompt + "\n\n" + userPrompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "temperature", 0.2));

        String jsonPayload = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(properties.llm().timeoutMs()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            String errDetail = extractApiErrorMessage(response.body());
            HttpStatus status = HttpStatus.resolve(response.statusCode());
            if (status == null) {
                status = HttpStatus.BAD_GATEWAY;
            }
            throw new ApiException(status, "LLM_API_ERROR",
                    String.format("Gemini API error (HTTP %d, model %s): %s", response.statusCode(), model, errDetail));
        }

        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();

        return parseModelJson(incidentId, model, content, response.body());
    }

    private AiInvestigationResponse callOpenAiWithRetry(Long incidentId, String model, String apiKey,
                                                        String systemPrompt, String userPrompt) {
        String baseUrl = properties.llm().baseUrl().replaceAll("/+$", "") + "/chat/completions";

        Map<String, Object> requestBody = Map.of(
                "model", model != null && !model.isBlank() ? model : "gpt-4o-mini",
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "temperature", 0.2,
                "response_format", Map.of("type", "json_object"));

        int maxRetries = 2;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String jsonPayload = objectMapper.writeValueAsString(requestBody);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl))
                        .timeout(Duration.ofMillis(properties.llm().timeoutMs()))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 400) {
                    String errDetail = extractApiErrorMessage(response.body());
                    HttpStatus status = HttpStatus.resolve(response.statusCode());
                    if (status == null) {
                        status = HttpStatus.BAD_GATEWAY;
                    }
                    if (response.statusCode() == 429 || response.statusCode() == 503) {
                        log.warn("OpenAI API returned {} on attempt {}/{}. Retrying with backoff...", response.statusCode(), attempt, maxRetries);
                        sleepWithJitter(attempt);
                        continue;
                    }
                    throw new ApiException(status, "LLM_API_ERROR",
                            "OpenAI API error (HTTP " + response.statusCode() + "): " + errDetail);
                }

                JsonNode root = objectMapper.readTree(response.body());
                String content = root.path("choices").path(0).path("message").path("content").asText();

                return parseModelJson(incidentId, model, content, response.body());
            } catch (ApiException ae) {
                lastException = ae;
                if (ae.getStatus().value() == 401 || ae.getStatus().value() == 403 || ae.getStatus().value() == 400) {
                    throw ae;
                }
            } catch (Exception ex) {
                lastException = ex;
                log.warn("OpenAI call failed on attempt {}/{}: {}", attempt, maxRetries, ex.getMessage());
                sleepWithJitter(attempt);
            }
        }

        throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "LLM_CALL_FAILED",
                "Failed to communicate with OpenAI API after retries: " + (lastException != null ? lastException.getMessage() : "Unknown error"));
    }

    private void sleepWithJitter(int attempt) {
        try {
            long baseDelay = 600L * attempt;
            long jitter = ThreadLocalRandom.current().nextLong(100, 300);
            Thread.sleep(baseDelay + jitter);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private AiInvestigationResponse parseModelJson(Long incidentId, String model, String content, String rawResponse) {
        try {
            JsonNode data = objectMapper.readTree(content);

            String summary = data.path("summary").asText("No summary provided.");

            List<SuspiciousEventDto> suspiciousSequence = new ArrayList<>();
            JsonNode seqNode = data.path("suspiciousSequence");
            if (seqNode.isArray()) {
                for (JsonNode item : seqNode) {
                    suspiciousSequence.add(new SuspiciousEventDto(
                            Instant.now(),
                            item.path("service").asText("unknown"),
                            item.path("event").asText(""),
                            item.path("suspicionReason").asText("")));
                }
            }

            List<RootCauseHypothesisDto> hypotheses = new ArrayList<>();
            JsonNode hypNode = data.path("rootCauseHypotheses");
            if (hypNode.isArray()) {
                for (JsonNode item : hypNode) {
                    List<String> evidence = new ArrayList<>();
                    JsonNode evNode = item.path("supportingEvidence");
                    if (evNode.isArray()) {
                        for (JsonNode ev : evNode) {
                            evidence.add(ev.asText());
                        }
                    }
                    hypotheses.add(new RootCauseHypothesisDto(
                            item.path("hypothesis").asText(""),
                            item.path("confidence").asText("MEDIUM"),
                            evidence,
                            item.path("reasoning").asText("")));
                }
            }

            List<RecommendationDto> recommendations = new ArrayList<>();
            JsonNode recNode = data.path("recommendations");
            if (recNode.isArray()) {
                for (JsonNode item : recNode) {
                    recommendations.add(new RecommendationDto(
                            item.path("action").asText(""),
                            item.path("priority").asText("SHORT_TERM"),
                            item.path("rationale").asText("")));
                }
            }

            return new AiInvestigationResponse(
                    null,
                    incidentId,
                    model,
                    summary,
                    suspiciousSequence,
                    hypotheses,
                    recommendations,
                    rawResponse,
                    Instant.now());
        } catch (Exception ex) {
            log.warn("Failed to parse JSON response from LLM, returning formatted payload: {}", ex.getMessage());
            return new AiInvestigationResponse(
                    null,
                    incidentId,
                    model,
                    content.length() > 500 ? content.substring(0, 500) : content,
                    List.of(new SuspiciousEventDto(Instant.now(), "system", "Model output generated",
                            "Model generated unstructured output")),
                    List.of(new RootCauseHypothesisDto("Inferred root cause from logs", "MEDIUM",
                            List.of("See raw response"), content)),
                    List.of(new RecommendationDto("Inspect application logs directly", "IMMEDIATE",
                            "Validate model output")),
                    rawResponse,
                    Instant.now());
        }
    }

    private String extractApiErrorMessage(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.has("error") && node.get("error").has("message")) {
                return node.get("error").get("message").asText();
            }
        } catch (Exception ignored) {
        }
        return body != null && body.length() > 200 ? body.substring(0, 200) : body;
    }
}
