package com.tracelens.service;

import com.tracelens.dto.ErrorGroupDetailResponse;
import com.tracelens.dto.ErrorGroupResponse;
import com.tracelens.dto.LogEntryDto;
import com.tracelens.dto.PagedResponse;
import com.tracelens.exception.ResourceNotFoundException;
import com.tracelens.repository.ErrorGroupRepository;
import com.tracelens.repository.LogRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ErrorGroupService {

    private final ErrorGroupRepository errorGroupRepository;
    private final LogRepository logRepository;

    public ErrorGroupService(ErrorGroupRepository errorGroupRepository, LogRepository logRepository) {
        this.errorGroupRepository = errorGroupRepository;
        this.logRepository = logRepository;
    }

    public PagedResponse<ErrorGroupResponse> listGroups(String serviceName, int limit, int offset) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        int boundedOffset = Math.max(0, offset);

        List<ErrorGroupResponse> items = errorGroupRepository.findAll(serviceName, boundedLimit, boundedOffset);
        long total = errorGroupRepository.countAll(serviceName);
        boolean hasMore = (boundedOffset + items.size()) < total;

        return new PagedResponse<>(items, total, boundedLimit, boundedOffset, hasMore);
    }

    public ErrorGroupDetailResponse getGroupDetail(String fingerprint) {
        ErrorGroupResponse group = errorGroupRepository.findByFingerprint(fingerprint)
                .orElseThrow(() -> new ResourceNotFoundException("Error group not found for fingerprint: " + fingerprint));

        List<LogEntryDto> sampleLogs = logRepository.findByFingerprint(fingerprint, 25);
        return new ErrorGroupDetailResponse(group, sampleLogs);
    }
}
