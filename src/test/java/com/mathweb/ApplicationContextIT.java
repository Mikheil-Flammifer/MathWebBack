package com.mathweb;

import com.mathweb.support.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test: the application starts on a real PostgreSQL, every migration applies,
 * and the JPA mappings match the schema (ddl-auto is "validate" in the test profile).
 */
class ApplicationContextIT extends AbstractIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Test
    void contextStarts_andAllMigrationsAreApplied() {
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().current()).isNotNull();
        assertThat(Arrays.stream(flyway.info().all()).map(MigrationInfo::getState))
                .noneMatch(state -> state.isFailed());
    }
}