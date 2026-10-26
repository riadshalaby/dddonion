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
 * Runs a {@link TransactionalUseCase} through the real pipeline against MariaDB: a failing use case leaves no write.
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

    @TestConfiguration
    static class TestUseCaseConfig {
        @Bean
        SaveCustomerService saveCustomerService(CustomerRepository customers) {
            return new SaveCustomerService(customers);
        }
    }

    @Container
    static MariaDBContainer maria = new MariaDBContainer("mariadb:11")
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
    CustomerRepository customers;

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
}
