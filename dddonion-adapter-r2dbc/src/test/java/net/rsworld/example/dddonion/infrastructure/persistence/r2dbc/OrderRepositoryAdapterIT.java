package net.rsworld.example.dddonion.infrastructure.persistence.r2dbc;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.math.BigDecimal;
import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.adapter.OrderRepositoryAdapter;
import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.repo.OrderR2dbcRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@Testcontainers
class OrderRepositoryAdapterIT {

    @Container
    static MariaDBContainer maria = new MariaDBContainer("mariadb:11")
            .withUsername("test")
            .withPassword("test")
            .withDatabaseName("test");

    static DatabaseClient db;
    static OrderRepositoryAdapter adapter;

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
        db.sql("""
            CREATE TABLE IF NOT EXISTS orders (
              id VARCHAR(64) PRIMARY KEY,
              customer_email VARCHAR(255) NOT NULL,
              total DECIMAL(19,2) NOT NULL,
              status VARCHAR(32) NOT NULL,
              version BIGINT NOT NULL DEFAULT 0,
              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            );
        """).fetch().rowsUpdated().block();

        var template = new R2dbcEntityTemplate(cf);
        var factory = new org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory(template);
        OrderR2dbcRepository repo = factory.getRepository(OrderR2dbcRepository.class);
        adapter = new OrderRepositoryAdapter(repo);
    }

    @AfterAll
    static void stop() {
        maria.stop();
    }

    @Test
    @DisplayName("Speichert eine platzierte Order und liest sie anschließend über findById wieder ein")
    void saveAndFind_optionalNotEmpty() {
        var o = new Order("a@b.com", new BigDecimal("12.34"));
        o.place(); // domain transition first, then persist – mirrors production flow

        StepVerifier.create(Mono.fromCompletionStage(adapter.save(o))
                        .then(Mono.fromCompletionStage(adapter.findById(o.id())))
                        .map(opt -> opt.orElseThrow(() -> new AssertionError("Order not found via adapter.findById"))))
                .expectNextMatches(found -> found.id().value().equals(o.id().value()))
                .verifyComplete();
    }
}
