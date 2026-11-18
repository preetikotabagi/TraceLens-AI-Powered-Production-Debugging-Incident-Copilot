package com.tracelens.controller;

import com.tracelens.dto.SeedResponse;
import com.tracelens.service.SeedDataService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seed")
public class SeedController {

    private final SeedDataService seedDataService;

    public SeedController(SeedDataService seedDataService) {
        this.seedDataService = seedDataService;
    }

    @PostMapping
    public ResponseEntity<SeedResponse> seedData() {
        SeedResponse response = seedDataService.seedRealisticData();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
