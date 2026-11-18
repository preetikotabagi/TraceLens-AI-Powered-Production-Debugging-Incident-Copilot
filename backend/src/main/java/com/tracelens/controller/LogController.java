package com.tracelens.controller;

import com.tracelens.dto.BulkLogIngestRequest;
import com.tracelens.dto.BulkLogIngestResponse;
import com.tracelens.dto.LogEntryDto;
import com.tracelens.dto.LogLevel;
import com.tracelens.dto.PagedResponse;
import com.tracelens.exception.ResourceNotFoundException;
import com.tracelens.service.LogService;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/logs")
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    @PostMapping
    public ResponseEntity<LogEntryDto> ingestSingle(@Valid @RequestBody LogEntryDto logEntry) {
        LogEntryDto saved = logService.ingest(logEntry);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping("/bulk")
    public ResponseEntity<BulkLogIngestResponse> ingestBulk(@Valid @RequestBody BulkLogIngestRequest request) {
        BulkLogIngestResponse response = logService.ingestBulk(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public PagedResponse<LogEntryDto> searchLogs(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) LogLevel level,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String fingerprint,
            @RequestParam(required = false) Long incidentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return logService.search(service, level, query, fingerprint, incidentId, startTime, endTime, limit, offset);
    }

    @GetMapping("/{id}")
    public LogEntryDto getLogById(@PathVariable Long id) {
        return logService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Log entry not found with ID: " + id));
    }
}
