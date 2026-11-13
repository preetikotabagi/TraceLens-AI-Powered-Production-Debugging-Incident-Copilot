package com.tracelens.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Safety net: the demo error generator must never run in production.
 * If demo mode is enabled while the {@code prod} profile is active, the application refuses to start.
 */
@Component
@ConditionalOnProperty(prefix = "tracelens.demo", name = "enabled", havingValue = "true")
public class DemoSafetyGuard {

    private static final Logger log = LoggerFactory.getLogger(DemoSafetyGuard.class);

    private final Environment environment;

    public DemoSafetyGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void verifyNotProduction() {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    "tracelens.demo.enabled=true is not allowed with the 'prod' profile. "
                            + "Disable the demo endpoints before running in production.");
        }
        log.warn("DEMO MODE ENABLED: /api/v1/demo/** endpoints can generate deliberate errors. "
                + "Development use only.");
    }
}
