package com.tracelens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the TraceLens backend.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class TraceLensApplication {

    public static void main(String[] args) {
        SpringApplication.run(TraceLensApplication.class, args);
    }
}
