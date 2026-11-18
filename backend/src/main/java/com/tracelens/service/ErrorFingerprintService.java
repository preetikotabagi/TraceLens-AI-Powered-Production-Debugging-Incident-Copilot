package com.tracelens.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Generates consistent error fingerprints using normalized error information.
 * Repeated errors with different dynamic parameters (UUIDs, timestamps, IDs)
 * yield the same fingerprint.
 */
@Service
public class ErrorFingerprintService {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern HEX_PATTERN =
            Pattern.compile("0x[0-9a-fA-F]+");
    private static final Pattern IP_PATTERN =
            Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d+)?\\b");
    private static final Pattern TIMESTAMP_PATTERN =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})?");
    private static final Pattern NUMBER_PATTERN =
            Pattern.compile("\\b\\d+\\b");
    private static final Pattern EXCEPTION_NAME_PATTERN =
            Pattern.compile("([a-zA-Z_$][a-zA-Z\\d_$]*\\.)*([a-zA-Z_$][a-zA-Z\\d_$]*(?:Exception|Error|Fault))");

    /**
     * Normalizes an error message by masking dynamic variables.
     */
    public String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "UnknownError";
        }

        String normalized = message;
        normalized = UUID_PATTERN.matcher(normalized).replaceAll("<UUID>");
        normalized = TIMESTAMP_PATTERN.matcher(normalized).replaceAll("<DATE>");
        normalized = IP_PATTERN.matcher(normalized).replaceAll("<IP>");
        normalized = HEX_PATTERN.matcher(normalized).replaceAll("<HEX>");
        normalized = NUMBER_PATTERN.matcher(normalized).replaceAll("<NUM>");
        normalized = normalized.replaceAll("\\s+", " ").trim();

        // Limit template size for storage
        if (normalized.length() > 500) {
            normalized = normalized.substring(0, 500);
        }

        return normalized;
    }

    /**
     * Extracts or infers an error type (e.g. "PaymentGatewayTimeoutException", "NullPointerException").
     */
    public String extractErrorType(String explicitType, String message, String stackTrace) {
        if (explicitType != null && !explicitType.isBlank()) {
            return cleanErrorTypeName(explicitType);
        }

        if (stackTrace != null && !stackTrace.isBlank()) {
            Matcher matcher = EXCEPTION_NAME_PATTERN.matcher(stackTrace);
            if (matcher.find()) {
                return cleanErrorTypeName(matcher.group());
            }
        }

        if (message != null && !message.isBlank()) {
            Matcher matcher = EXCEPTION_NAME_PATTERN.matcher(message);
            if (matcher.find()) {
                return cleanErrorTypeName(matcher.group());
            }
        }

        return "GenericApplicationError";
    }

    private String cleanErrorTypeName(String raw) {
        int dot = raw.lastIndexOf('.');
        if (dot >= 0 && dot < raw.length() - 1) {
            return raw.substring(dot + 1);
        }
        return raw.trim();
    }

    /**
     * Extracts top 2 application frames from stack trace for stable fingerprinting.
     */
    public String extractTopFrames(String stackTrace) {
        if (stackTrace == null || stackTrace.isBlank()) {
            return "";
        }

        StringBuilder frames = new StringBuilder();
        String[] lines = stackTrace.split("\\r?\\n");
        int count = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("at ") && !trimmed.contains("java.base") && !trimmed.contains("org.springframework")) {
                frames.append(trimmed).append(";");
                count++;
                if (count >= 2) {
                    break;
                }
            }
        }
        return frames.toString();
    }

    /**
     * Generates a stable SHA-256 fingerprint hex string.
     */
    public String generateFingerprint(String serviceName, String errorType, String messageTemplate, String topFrames) {
        String service = serviceName != null ? serviceName.trim().toLowerCase() : "unknown";
        String type = errorType != null ? errorType.trim() : "GenericApplicationError";
        String template = messageTemplate != null ? messageTemplate.trim() : "";
        String frames = topFrames != null ? topFrames.trim() : "";

        String raw = service + "::" + type + "::" + template + "::" + frames;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder("fp_");
            // Take 16 hex characters for a clean, compact, readable fingerprint
            for (int i = 0; i < 8; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // fallback
            return "fp_" + Integer.toHexString(raw.hashCode());
        }
    }
}
