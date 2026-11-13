package com.tracelens.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tracelens.config.TraceLensProperties;
import com.tracelens.dto.DemoSimulationResponse;
import com.tracelens.dto.ScenarioType;
import com.tracelens.exception.BadRequestException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class DemoErrorServiceTest {

    private static DemoErrorService serviceWithMaxDelay(int maxDelayMs) {
        return new DemoErrorService(new TraceLensProperties(
                new TraceLensProperties.App("TraceLens", "0.1.0"),
                new TraceLensProperties.Demo(true, maxDelayMs),
                new TraceLensProperties.Cors(List.of())));
    }

    @Test
    void databaseFailureScenarioThrowsDataAccessException() {
        DemoErrorService service = serviceWithMaxDelay(1000);

        assertThatThrownBy(() -> service.simulate(ScenarioType.DATABASE_FAILURE, null))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void invalidRequestScenarioThrowsBadRequest() {
        DemoErrorService service = serviceWithMaxDelay(1000);

        assertThatThrownBy(() -> service.simulate(ScenarioType.INVALID_REQUEST, null))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("DEMO_INVALID_REQUEST");
    }

    @Test
    void slowRequestScenarioWaitsAtLeastTheRequestedDelay() {
        DemoErrorService service = serviceWithMaxDelay(1000);

        DemoSimulationResponse response = service.simulate(ScenarioType.SLOW_REQUEST, 60);

        assertThat(response.scenario()).isEqualTo(ScenarioType.SLOW_REQUEST);
        assertThat(response.elapsedMs()).isGreaterThanOrEqualTo(50);
    }

    @Test
    void slowRequestScenarioRejectsDelayAboveConfiguredMaximum() {
        DemoErrorService service = serviceWithMaxDelay(100);

        assertThatThrownBy(() -> service.simulate(ScenarioType.SLOW_REQUEST, 500))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("DEMO_DELAY_TOO_LARGE");
    }
}
