package net.rsworld.example.dddonion.application.customer.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import net.rsworld.example.dddonion.application.context.ActorType;
import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.domain.common.Money;
import net.rsworld.example.dddonion.domain.customer.command.RegisterCustomerCommand;
import net.rsworld.example.dddonion.domain.customer.model.Customer;
import net.rsworld.example.dddonion.domain.customer.model.CustomerEmail;
import net.rsworld.example.dddonion.domain.customer.model.EmailAlreadyRegisteredException;
import net.rsworld.example.dddonion.domain.customer.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RegisterCustomerServiceTest {

    private static final UseCaseMetadata META = new UseCaseMetadata(ActorType.UNKNOWN, "test", "corr-1");
    private static final RegisterCustomerCommand CMD =
            new RegisterCustomerCommand(new CustomerEmail("a@example.com"), Money.of("100"));

    @Test
    void savesNewCustomerAndReturnsItsId() {
        var customers = mock(CustomerRepository.class);
        when(customers.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(new RegisterCustomerService(customers).handle(CMD, META))
                .expectNextCount(1)
                .verifyComplete();

        var captor = ArgumentCaptor.forClass(Customer.class);
        verify(customers).save(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().email()).isEqualTo(CMD.email());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().creditLimit())
                .isEqualTo(CMD.creditLimit());
    }

    @Test
    void propagatesDuplicateEmail() {
        var customers = mock(CustomerRepository.class);
        when(customers.save(any())).thenReturn(Mono.error(new EmailAlreadyRegisteredException(CMD.email())));

        StepVerifier.create(new RegisterCustomerService(customers).handle(CMD, META))
                .verifyError(EmailAlreadyRegisteredException.class);
    }
}
