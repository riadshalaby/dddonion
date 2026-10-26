package net.rsworld.example.dddonion.infrastructure.persistence.r2dbc;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import net.rsworld.example.dddonion.domain.common.ConcurrentAggregateUpdateException;
import net.rsworld.example.dddonion.domain.common.Money;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import net.rsworld.example.dddonion.domain.order.model.OrderStatus;
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
    static MariaDBContainer maria = new MariaDBContainer("mariadb:11.8")
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
              customer_id VARCHAR(36) NOT NULL,
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
        adapter = new OrderRepositoryAdapter(repo, template);
    }

    @AfterAll
    static void stop() {
        maria.stop();
    }

    @Test
    @DisplayName("Speichert eine platzierte Order und liest sie anschließend über findById wieder ein")
    void saveAndFind() {
        var o = new Order(CustomerId.newId(), new Money(new BigDecimal("12.34")));
        o.place(); // domain transition first, then persist – mirrors production flow

        StepVerifier.create(adapter.save(o)
                        .then(adapter.findById(o.id()))
                        .switchIfEmpty(Mono.error(() -> new AssertionError("Order not found via adapter.findById"))))
                .expectNextMatches(found -> found.id().value().equals(o.id().value()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Speichert die Domain-Version: eine platzierte Order hat Version 1, nach pay() Version 2")
    void persistsDomainVersion() {
        var o = placedOrder();

        Mono<Order> flow = adapter.save(o)
                .then(load(o.id()))
                .doOnNext(loaded -> assertEquals(1L, loaded.version()))
                .flatMap(loaded -> {
                    loaded.pay();
                    return adapter.save(loaded);
                })
                .then(load(o.id()));

        StepVerifier.create(flow)
                .assertNext(paid -> {
                    assertEquals(2L, paid.version());
                    assertEquals(OrderStatus.PAID, paid.status());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Ein veralteter Stand führt beim Update zu ConcurrentAggregateUpdateException")
    void staleUpdateFailsWithConcurrentAggregateUpdate() {
        var o = placedOrder();

        Mono<Order> flow = adapter.save(o)
                .then(Mono.zip(load(o.id()), load(o.id())))
                .flatMap(copies -> {
                    copies.getT1().pay();
                    copies.getT2().pay();
                    return adapter.save(copies.getT1()).then(adapter.save(copies.getT2()));
                });

        StepVerifier.create(flow)
                .expectError(ConcurrentAggregateUpdateException.class)
                .verify();
    }

    @Test
    @DisplayName("Ein Update lässt created_at unverändert")
    void updateKeepsCreatedAt() {
        var o = placedOrder();
        Mono<LocalDateTime> readCreatedAt = db.sql("SELECT created_at FROM orders WHERE id = :id")
                .bind("id", o.id().value())
                .map(row -> row.get("created_at", LocalDateTime.class))
                .one();

        Mono<LocalDateTime[]> flow = adapter.save(o).then(readCreatedAt).flatMap(before -> load(o.id())
                .delayElement(Duration.ofMillis(1500))
                .flatMap(loaded -> {
                    loaded.pay();
                    return adapter.save(loaded);
                })
                .then(readCreatedAt)
                .map(after -> new LocalDateTime[] {before, after}));

        StepVerifier.create(flow).assertNext(v -> assertEquals(v[0], v[1])).verifyComplete();
    }

    private static Order placedOrder() {
        var o = new Order(CustomerId.newId(), new Money(new BigDecimal("12.34")));
        o.place();
        return o;
    }

    private static Mono<Order> load(OrderId id) {
        return adapter.findById(id)
                .switchIfEmpty(Mono.error(() -> new AssertionError("Order not found: " + id.value())));
    }
}
