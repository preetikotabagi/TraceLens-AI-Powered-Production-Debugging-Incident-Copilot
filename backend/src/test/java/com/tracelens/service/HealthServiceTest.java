package com.tracelens.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.tracelens.config.TraceLensProperties;
import com.tracelens.dto.HealthResponse;
import com.tracelens.dto.HealthResponse.Status;
import com.tracelens.repository.DatabaseHealthRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class HealthServiceTest {

    @Mock
    private DatabaseHealthRepository repository;

    private HealthService service;

    @BeforeEach
    void setUp() {
        TraceLensProperties properties = new TraceLensProperties(
                new TraceLensProperties.App("TraceLens", "0.1.0"),
                new TraceLensProperties.Demo(false, 10000),
                new TraceLensProperties.Cors(List.of("http://localhost:5173")));
        service = new HealthService(repository, properties);
    }

    @Test
    void reportsUpWithMigrationVersionWhenDatabaseResponds() {
        when(repository.latestMigrationVersion()).thenReturn(Optional.of("1"));

        HealthResponse response = service.check();

        assertThat(response.status()).isEqualTo(Status.UP);
        assertThat(response.application().name()).isEqualTo("TraceLens");
        assertThat(response.database().status()).isEqualTo(Status.UP);
        assertThat(response.database().latencyMs()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(response.database().migrationVersion()).isEqualTo("1");
        assertThat(response.database().error()).isNull();
    }

    @Test
    void reportsDownWithGenericErrorWhenDatabaseFails() {
        doThrow(new DataAccessResourceFailureException("jdbc:postgresql://secret-host:5432 refused"))
                .when(repository).ping();

        HealthResponse response = service.check();

        assertThat(response.status()).isEqualTo(Status.DOWN);
        assertThat(response.database().status()).isEqualTo(Status.DOWN);
        assertThat(response.database().latencyMs()).isNull();
        // The response must not leak connection details.
        assertThat(response.database().error()).isEqualTo("Database connection failed");
    }

    @Test
    void staysUpWhenOnlyTheMigrationLookupFails() {
        when(repository.latestMigrationVersion())
                .thenThrow(new DataAccessResourceFailureException("table missing"));

        HealthResponse response = service.check();

        assertThat(response.status()).isEqualTo(Status.UP);
        assertThat(response.database().migrationVersion()).isNull();
    }
}
