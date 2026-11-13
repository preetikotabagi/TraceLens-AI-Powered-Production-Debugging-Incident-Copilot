package com.tracelens.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Typed application settings, bound from the {@code tracelens.*} keys in application.yml
 * (which in turn read environment variables).
 */
@ConfigurationProperties(prefix = "tracelens")
public record TraceLensProperties(
        @DefaultValue App app,
        @DefaultValue Demo demo,
        @DefaultValue Cors cors) {

    public record App(
            @DefaultValue("TraceLens") String name,
            @DefaultValue("0.1.0") String version) {
    }

    /**
     * Settings for the development-only demo error generator. Disabled unless explicitly enabled.
     */
    public record Demo(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("10000") int maxDelayMs) {
    }

    public record Cors(
            @DefaultValue({"http://localhost:5173", "http://localhost:3000"}) List<String> allowedOrigins) {
    }
}
