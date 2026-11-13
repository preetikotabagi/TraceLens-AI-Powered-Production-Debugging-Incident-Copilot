package com.tracelens.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request an id, puts it in the logging context (MDC) so each log line of that request
 * carries it, echoes it in the {@code X-Request-Id} response header, and logs one summary line
 * per request (method, path, status, duration).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final String HEALTH_PATH = "/api/v1/health";

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);

        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            logCompletion(request, response.getStatus(), durationMs);
            MDC.remove(MDC_KEY);
        }
    }

    private void logCompletion(HttpServletRequest request, int status, long durationMs) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        // Health checks run every few seconds; keep them out of the normal log level.
        boolean quiet = HEALTH_PATH.equals(path) && status < 500;
        if (quiet && !log.isDebugEnabled()) {
            return;
        }
        var builder = quiet ? log.atDebug() : (status >= 500 ? log.atError() : log.atInfo());
        builder.addKeyValue("http.method", method)
                .addKeyValue("http.path", path)
                .addKeyValue("http.status", status)
                .addKeyValue("durationMs", durationMs)
                .log("HTTP {} {} -> {} ({} ms)", method, path, status, durationMs);
    }

    private static String resolveRequestId(String incoming) {
        if (incoming != null && SAFE_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}
