package io.redlink.more.data.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@JdbcTest
@Import(StudyRepository.class)
class StudyRepositoryTokenIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("more_test")
            .withUsername("more")
            .withPassword("more");

    @DynamicPropertySource
    static void registerDataSourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StudyRepository repository;

    @BeforeEach
    void initSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS participant_observation_properties");
        jdbcTemplate.execute("CREATE TABLE participant_observation_properties (study_id BIGINT NOT NULL, participant_id INT NOT NULL, " +
                "observation_id INT NOT NULL, properties JSONB, PRIMARY KEY (study_id, participant_id, observation_id))");
    }

    @Test
    void aTokenStoredForSeveralObservationsResolvesToNone() {
        jdbcTemplate.update("INSERT INTO participant_observation_properties VALUES (96, 11, 8, '{\"token\": \"shared\"}')");
        jdbcTemplate.update("INSERT INTO participant_observation_properties VALUES (96, 11, 15, '{\"token\": \"shared\"}')");

        assertThat(repository.getRoutingInfoAndObservationIdByToken("shared")).isEmpty();
        assertThat(repository.getRoutingInfoAndObservationIdByToken(96L, "shared")).isEmpty();
    }

    @Test
    void anUnknownTokenResolvesToNone() {
        assertThat(repository.getRoutingInfoAndObservationIdByToken("unknown")).isEmpty();
    }

    @Test
    void mergingPropertiesKeepsThemPerObservation() {
        jdbcTemplate.update("INSERT INTO participant_observation_properties VALUES (96, 11, 15, '{\"token\": \"lime\"}')");

        repository.mergeParticipantProperties(96L, 11, 8, java.util.Map.of("seed", 42));

        assertThat(repository.getParticipantWithObservationProperties(96L, 11, 8)).contains(java.util.Map.of("seed", 42));
        assertThat(repository.getParticipantWithObservationProperties(96L, 11, 15)).contains(java.util.Map.of("token", "lime"));
    }
}
