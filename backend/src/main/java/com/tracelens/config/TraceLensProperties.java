package com.tracelens.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Typed application settings, bound from the {@code tracelens.*} keys in application.yml.
 */
@ConfigurationProperties(prefix = "tracelens")
public record TraceLensProperties(
        @DefaultValue App app,
        @DefaultValue Demo demo,
        @DefaultValue Cors cors,
        @DefaultValue Llm llm,
        @DefaultValue IncidentRules rules) {

    @ConstructorBinding
    public TraceLensProperties(
            @DefaultValue App app,
            @DefaultValue Demo demo,
            @DefaultValue Cors cors,
            @DefaultValue Llm llm,
            @DefaultValue IncidentRules rules) {
        this.app = app;
        this.demo = demo;
        this.cors = cors;
        this.llm = llm;
        this.rules = rules;
    }

    public TraceLensProperties(App app, Demo demo, Cors cors) {
        this(app, demo, cors,
                new Llm(true, "", "gpt-4o-mini", "https://api.openai.com/v1", "openai", 25000),
                new IncidentRules(5, 5, 1, 3));
    }

    public record App(
            @DefaultValue("TraceLens") String name,
            @DefaultValue("0.1.0") String version) {
    }

    /**
     * Settings for the development-only demo error generator.
     */
    public record Demo(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("10000") int maxDelayMs) {
    }

    public record Cors(
            @DefaultValue({"http://localhost:5173", "http://localhost:3000"}) List<String> allowedOrigins) {
    }

    /**
     * Settings for the LLM-powered incident investigation copilot.
     */
    public record Llm(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("") String apiKey,
            @DefaultValue("gpt-4o-mini") String model,
            @DefaultValue("https://api.openai.com/v1") String baseUrl,
            @DefaultValue("openai") String provider,
            @DefaultValue("25000") int timeoutMs) {
    }

    /**
     * Rule-based incident detection thresholds.
     */
    public record IncidentRules(
            @DefaultValue("5") int errorWindowMinutes,
            @DefaultValue("5") int errorThreshold,
            @DefaultValue("1") int criticalErrorThreshold,
            @DefaultValue("3") int recurrenceThreshold) {
    }
}
