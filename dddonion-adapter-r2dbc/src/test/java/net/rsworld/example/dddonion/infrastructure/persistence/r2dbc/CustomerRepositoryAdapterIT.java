package net.rsworld.example.dddonion.infrastructure.persistence.r2dbc;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.math.BigDecimal;
import net.rsworld.example.dddonion.domain.common.ConcurrentAggregateUpdateException;
import net.rsworld.example.dddonion.domain.common.Money;
import net.rsworld.example.dddonion.domain.customer.model.Customer;
import net.rsworld.example.dddonion.domain.customer.model.CustomerEmail;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import net.rsworld.example.dddonion.domain.customer.model.EmailAlreadyRegisteredException;
import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.adapter.CustomerRepositoryAdapter;
import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.repo.CustomerR2dbcRepository;
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
class CustomerRepositoryAdapterIT {

    @Container
    static MariaDBContainer maria = new MariaDBContainer("mariadb:11")
            .withUsername("test")
            .withPassword("test")
            .withDatabaseName("test");

    static CustomerRepositoryAdapter adapter;
    static CustomerR2dbcRepository realRepo;
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
        db.sql("""
            CREATE TABLE IF NOT EXISTS customers (
              id VARCHAR(36) PRIMARY KEY,
              email VARCHAR(254) NOT NULL,
              credit_limit DECIMAL(19,2) NOT NULL,
              open_amount DECIMAL(19,2) NOT NULL,
              version BIGINT NOT NULL,
              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              CONSTRAINT uq_customers_email UNIQUE (email)
            );
        """).fetch().rowsUpdated().block();

        var template = new R2dbcEntityTemplate(cf);
        var factory = new org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory(template);
        realRepo = factory.getRepository(CustomerR2dbcRepository.class);
        adapter = new CustomerRepositoryAdapter(realRepo);
    }

    @AfterAll
    static void stop() {
        maria.stop();
    }

    private static Customer newCustomer(String email) {
        return new Customer(new CustomerEmail(email), new Money(new BigDecimal("1000.00")));
    }

    private static Mono<Customer> load(CustomerId id) {
        return adapter.findById(id).switchIfEmpty(Mono.error(() -> new AssertionError("Customer not found")));
    }

    @Test
    @DisplayName("Speichert einen neuen Customer und liest ihn wieder ein")
    void savesAndFindsNewCustomer() {
        Customer c = newCustomer("save-find@example.com");

        StepVerifier.create(adapter.save(c).then(load(c.id())))
                .assertNext(found -> {
                    assertEquals(c.id().value(), found.id().value());
                    assertEquals("save-find@example.com", found.email().value());
                    assertEquals(
                            0,
                            new BigDecimal("1000.00")
                                    .compareTo(found.creditLimit().amount()));
                    assertEquals(0, BigDecimal.ZERO.compareTo(found.openAmount().amount()));
                    assertEquals(0L, found.version());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Ein Update erhöht die Version um eins")
    void updateIncrementsVersion() {
        Customer c = newCustomer("update@example.com");

        Mono<long[]> flow = adapter.save(c).then(load(c.id())).flatMap(before -> {
            before.reserveCredit(new Money(new BigDecimal("10.00")));
            return adapter.save(before).then(load(c.id())).map(after -> new long[] {before.version(), after.version()});
        });

        StepVerifier.create(flow).assertNext(v -> assertEquals(v[0] + 1, v[1])).verifyComplete();
    }

    @Test
    @DisplayName("Ein veralteter Stand führt zu ConcurrentAggregateUpdateException")
    void staleUpdateFailsWithConcurrentAggregateUpdate() {
        Customer c = newCustomer("stale@example.com");

        Mono<Void> flow = adapter.save(c)
                .then(Mono.zip(load(c.id()), load(c.id())))
                .flatMap(copies -> {
                    copies.getT1().reserveCredit(new Money(new BigDecimal("1.00")));
                    copies.getT2().reserveCredit(new Money(new BigDecimal("2.00")));
                    return adapter.save(copies.getT1()).then(adapter.save(copies.getT2()));
                })
                .then();

        StepVerifier.create(flow)
                .expectError(ConcurrentAggregateUpdateException.class)
                .verify();
    }

    @Test
    @DisplayName("Eine bereits registrierte E-Mail führt zu EmailAlreadyRegisteredException")
    void duplicateEmailFailsWithEmailAlreadyRegistered() {
        Customer first = newCustomer("dup@example.com");
        Customer second = newCustomer("dup@example.com");

        StepVerifier.create(adapter.save(first).then(adapter.save(second)))
                .expectError(EmailAlreadyRegisteredException.class)
                .verify();
    }

    @Test
    @DisplayName("Ein doppelter Primärschlüssel wird nicht als E-Mail-Konflikt gemeldet")
    void duplicateIdIsNotReportedAsEmailConflict() {
        Customer first = newCustomer("pk-one@example.com");
        // Same id, different email; the repository claims the row does not exist, so the adapter takes the insert path.
        Customer second = new Customer(
                first.id(),
                new CustomerEmail("pk-two@example.com"),
                new Money(new BigDecimal("5.00")),
                Money.zero(),
                0L);
        CustomerR2dbcRepository blind = (CustomerR2dbcRepository) java.lang.reflect.Proxy.newProxyInstance(
                CustomerR2dbcRepository.class.getClassLoader(),
                new Class<?>[] {CustomerR2dbcRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("existsById")) {
                        return Mono.just(false);
                    }
                    try {
                        return method.invoke(realRepo, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        var blindAdapter = new CustomerRepositoryAdapter(blind);

        StepVerifier.create(adapter.save(first).then(blindAdapter.save(second)))
                .expectErrorSatisfies(e -> {
                    assertFalse(e instanceof EmailAlreadyRegisteredException, "unexpected: " + e);
                    assertInstanceOf(org.springframework.dao.DuplicateKeyException.class, e);
                })
                .verify();
    }

    @Test
    @DisplayName("Ein Update lässt created_at unverändert")
    void updateKeepsCreatedAt() {
        Customer c = newCustomer("created-at@example.com");
        Mono<java.time.LocalDateTime> readCreatedAt = db.sql("SELECT created_at FROM customers WHERE id = :id")
                .bind("id", c.id().value())
                .map(row -> row.get("created_at", java.time.LocalDateTime.class))
                .one();

        Mono<java.time.LocalDateTime[]> flow = adapter.save(c)
                .then(readCreatedAt)
                .flatMap(before -> load(c.id())
                        .delayElement(java.time.Duration.ofMillis(1500))
                        .flatMap(loaded -> {
                            loaded.reserveCredit(new Money(new BigDecimal("3.00")));
                            return adapter.save(loaded);
                        })
                        .then(readCreatedAt)
                        .map(after -> new java.time.LocalDateTime[] {before, after}));

        StepVerifier.create(flow).assertNext(v -> assertEquals(v[0], v[1])).verifyComplete();
    }
}
