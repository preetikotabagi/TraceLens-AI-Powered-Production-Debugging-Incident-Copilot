package com.tracelens.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the browser-based dashboard (a different origin) to call the API.
 * Allowed origins come from configuration, never a wildcard.
 */
@Configuration
@EnableConfigurationProperties(TraceLensProperties.class)
public class CorsConfig implements WebMvcConfigurer {

    private final TraceLensProperties properties;

    public CorsConfig(TraceLensProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(properties.cors().allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "X-Request-Id")
                .exposedHeaders("X-Request-Id")
                .maxAge(3600);
    }
}
