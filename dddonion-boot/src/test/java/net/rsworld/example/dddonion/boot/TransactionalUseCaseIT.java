package net.rsworld.example.dddonion.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import net.rsworld.example.dddonion.application.context.ActorType;
import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.architecture.TransactionalUseCase;
import net.rsworld.example.dddonion.architecture.UseCase;
import net.rsworld.example.dddonion.bootstrap.DddOnionApplication;
import net.rsworld.example.dddonion.domain.common.Money;
import net.rsworld.example.dddonion.domain.customer.model.Customer;
import net.rsworld.example.dddonion.domain.customer.model.CustomerEmail;
import net.rsworld.example.dddonion.domain.customer.repository.CustomerRepository;
import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.domain.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Runs a {@link TransactionalUseCase} through the real pipeline against MariaDB: a failing use case leaves no write,
 * neither for the customer nor for the order.
 */
@Testcontainers
@Import(TransactionalUseCaseIT.TestUseCaseConfig.class)
@SpringBootTest(classes = DddOnionApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransactionalUseCaseIT {

    @UseCase(name = "test.save-customer", kind = UseCase.Kind.COMMAND)
    public interface SaveCustomerUseCase {
        Mono<Void> handle(Customer customer, boolean failAfterSave, UseCaseMetadata metadata);
    }

    @TransactionalUseCase
    static class SaveCustomerService implements SaveCustomerUseCase {
        private final CustomerRepository customers;

        SaveCustomerService(CustomerRepository customers) {
            this.customers = customers;
        }

        @Override
        public Mono<Void> handle(Customer customer, boolean failAfterSave, UseCaseMetadata metadata) {
            return customers
                    .save(customer)
                    .then(Mono.defer(() ->
                            failAfterSave ? Mono.error(new IllegalStateException("fail after save")) : Mono.empty()));
        }
    }

    @UseCase(name = "test.save-customer-and-order", kind = UseCase.Kind.COMMAND)
    public interface SaveCustomerAndOrderUseCase {
        Mono<Void> handle(Customer customer, Order order, boolean failAfterSave, UseCaseMetadata metadata);
    }

    /** Writes through both adapters, so a write outside the transaction would survive the rollback. */
    @TransactionalUseCase
    static class SaveCustomerAndOrderService implements SaveCustomerAndOrderUseCase {
        private final CustomerRepository customers;
        private final OrderRepository orders;

        SaveCustomerAndOrderService(CustomerRepository customers, OrderRepository orders) {
            this.customers = customers;
            this.orders = orders;
        }

        @Override
        public Mono<Void> handle(Customer customer, Order order, boolean failAfterSave, UseCaseMetadata metadata) {
            return customers
                    .save(customer)
                    .then(Mono.defer(() -> orders.save(order)))
                    .then(Mono.defer(() ->
                            failAfterSave ? Mono.error(new IllegalStateException("fail after save")) : Mono.empty()));
        }
    }

    @TestConfiguration
    static class TestUseCaseConfig {
        @Bean
        SaveCustomerService saveCustomerService(CustomerRepository customers) {
            return new SaveCustomerService(customers);
        }

        @Bean
        SaveCustomerAndOrderService saveCustomerAndOrderService(CustomerRepository customers, OrderRepository orders) {
            return new SaveCustomerAndOrderService(customers, orders);
        }
    }

    @Container
    static MariaDBContainer maria = new MariaDBContainer("mariadb:11.8")
            .withUsername("test")
            .withPassword("test")
            .withDatabaseName("test");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.liquibase.url",
                () -> String.format(
                        "jdbc:mariadb://%s:%d/%s",
                        maria.getHost(), maria.getFirstMappedPort(), maria.getDatabaseName()));
        registry.add("spring.liquibase.user", maria::getUsername);
        registry.add("spring.liquibase.password", maria::getPassword);

        registry.add(
                "spring.r2dbc.url",
                () -> String.format(
                        "r2dbc:mariadb://%s:%d/%s",
                        maria.getHost(), maria.getFirstMappedPort(), maria.getDatabaseName()));
        registry.add("spring.r2dbc.username", maria::getUsername);
        registry.add("spring.r2dbc.password", maria::getPassword);
    }

    @Autowired
    SaveCustomerUseCase useCase;

    @Autowired
    SaveCustomerAndOrderUseCase customerAndOrderUseCase;

    @Autowired
    CustomerRepository customers;

    @Autowired
    OrderRepository orders;

    private static final UseCaseMetadata METADATA = new UseCaseMetadata(ActorType.UNKNOWN, "test", "it-tx");

    private static Customer newCustomer() {
        return new Customer(
                new CustomerEmail("tx-" + UUID.randomUUID() + "@example.com"), new Money(new BigDecimal("100.00")));
    }

    @Test
    void rollsBackWriteWhenUseCaseFails() {
        Customer customer = newCustomer();

        StepVerifier.create(useCase.handle(customer, true, METADATA))
                .expectError(IllegalStateException.class)
                .verify();

        StepVerifier.create(customers.findById(customer.id())).verifyComplete();
    }

    @Test
    void commitsWriteWhenUseCaseSucceeds() {
        Customer customer = newCustomer();

        StepVerifier.create(useCase.handle(customer, false, METADATA)).verifyComplete();

        StepVerifier.create(customers.findById(customer.id()))
                .assertNext(found -> assertThat(found.id()).isEqualTo(customer.id()))
                .verifyComplete();
    }

    @Test
    void rollsBackCustomerAndOrderWhenUseCaseFails() {
        Customer customer = newCustomer();
        Order order = new Order(customer.id(), new Money(new BigDecimal("10.00")));

        StepVerifier.create(customerAndOrderUseCase.handle(customer, order, true, METADATA))
                .expectError(IllegalStateException.class)
                .verify();

        StepVerifier.create(customers.findById(customer.id())).verifyComplete();
        StepVerifier.create(orders.findById(order.id())).verifyComplete();
    }

    @Test
    void commitsCustomerAndOrderWhenUseCaseSucceeds() {
        Customer customer = newCustomer();
        Order order = new Order(customer.id(), new Money(new BigDecimal("10.00")));

        StepVerifier.create(customerAndOrderUseCase.handle(customer, order, false, METADATA))
                .verifyComplete();

        StepVerifier.create(customers.findById(customer.id()))
                .expectNextCount(1)
                .verifyComplete();
        StepVerifier.create(orders.findById(order.id())).expectNextCount(1).verifyComplete();
    }
}
