package com.tracelens.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DemoSafetyGuardTest {

    @Test
    void refusesToStartWhenDemoIsEnabledInProduction() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        DemoSafetyGuard guard = new DemoSafetyGuard(environment);

        assertThatThrownBy(guard::verifyNotProduction)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prod");
    }

    @Test
    void allowsDemoInDevelopment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        DemoSafetyGuard guard = new DemoSafetyGuard(environment);

        assertThatCode(guard::verifyNotProduction).doesNotThrowAnyException();
    }
}
