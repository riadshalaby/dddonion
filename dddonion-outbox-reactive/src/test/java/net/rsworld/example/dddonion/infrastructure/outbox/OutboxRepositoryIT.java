package net.rsworld.example.dddonion.infrastructure.outbox;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;
import reactor.test.StepVerifier;

@Testcontainers
class OutboxRepositoryIT {

    @Container
    static MariaDBContainer maria = new MariaDBContainer("mariadb:11")
            .withUsername("test")
            .withPassword("test")
            .withDatabaseName("test");

    static OutboxRepository repo;
    static DatabaseClient db;

    @BeforeAll
    static void init() {
        ConnectionFactoryOptions options = ConnectionFactoryOptions.builder()
                .option(DRIVER, "mariadb")
                .option(HOST, maria.getHost())
                .option(PORT, maria.getFirstMappedPort())
                .option(USER, maria.getUsername())
                .option(PASSWORD, maria.getPassword())
                .option(DATABASE, maria.getDatabaseName())
                .build();

        ConnectionFactory cf = ConnectionFactories.get(options);
        db = DatabaseClient.create(cf);
        repo = new OutboxRepository(db);

        db.sql("""
            CREATE TABLE IF NOT EXISTS outbox_events (
              id VARCHAR(64) PRIMARY KEY,
              aggregate_id VARCHAR(64) NOT NULL,
              sequence BIGINT NOT NULL,
              type VARCHAR(128) NOT NULL,
              occurred_at TIMESTAMP NOT NULL,
              payload_json LONGTEXT NOT NULL,
              published BOOLEAN NOT NULL DEFAULT FALSE
            );
        """).fetch().rowsUpdated().block();
    }

    @AfterAll
    static void down() {
        maria.stop();
    }

    @Test
    @DisplayName("Löscht beim Cleanup nur veröffentlichte Outbox-Events, die älter als der Cutoff sind")
    void save_and_cleanup() {
        var older = Instant.now().minusSeconds(60 * 60 * 24 * 14);
        repo.save("agg1", 1L, "T", older, "{}").block();
        repo.save("agg1", 2L, "T", Instant.now(), "{}").block();
        db.sql("UPDATE outbox_events SET published=true WHERE sequence=1")
                .fetch()
                .rowsUpdated()
                .block();

        StepVerifier.create(repo.deletePublishedOlderThan(Instant.now().minusSeconds(60 * 60 * 24 * 7)))
                .expectNextMatches(rows -> rows >= 1)
                .verifyComplete();
    }
}
