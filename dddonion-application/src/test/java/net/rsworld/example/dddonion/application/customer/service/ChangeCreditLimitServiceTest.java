package net.rsworld.example.dddonion.application.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.rsworld.example.dddonion.application.context.ActorType;
import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.architecture.TransactionalUseCase;
import net.rsworld.example.dddonion.domain.common.Money;
import net.rsworld.example.dddonion.domain.customer.command.ChangeCreditLimitCommand;
import net.rsworld.example.dddonion.domain.customer.model.Customer;
import net.rsworld.example.dddonion.domain.customer.model.CustomerEmail;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import net.rsworld.example.dddonion.domain.customer.model.CustomerNotFoundException;
import net.rsworld.example.dddonion.domain.customer.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ChangeCreditLimitServiceTest {

    private static final UseCaseMetadata META = new UseCaseMetadata(ActorType.UNKNOWN, "test", "corr-1");

    private CustomerRepository customers;
    private ChangeCreditLimitService service;

    @BeforeEach
    void setUp() {
        customers = mock(CustomerRepository.class);
        service = new ChangeCreditLimitService(customers);
    }

    @Test
    void changesLimitAndSaves() {
        var id = CustomerId.newId();
        var loaded = new Customer(id, new CustomerEmail("a@example.com"), Money.of("100"), Money.zero(), 0L);
        when(customers.findById(id)).thenReturn(Mono.just(loaded));
        when(customers.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.handle(new ChangeCreditLimitCommand(id, Money.of("5")), META))
                .verifyComplete();

        var captor = ArgumentCaptor.forClass(Customer.class);
        verify(customers).save(captor.capture());
        assertThat(captor.getValue().creditLimit()).isEqualTo(Money.of("5"));
    }

    @Test
    void failsWithNotFoundForUnknownCustomer() {
        var id = CustomerId.newId();
        when(customers.findById(id)).thenReturn(Mono.empty());

        StepVerifier.create(service.handle(new ChangeCreditLimitCommand(id, Money.of("5")), META))
                .verifyError(CustomerNotFoundException.class);

        verify(customers, never()).save(any());
    }

    @Test
    void isTransactional() {
        assertThat(ChangeCreditLimitService.class.isAnnotationPresent(TransactionalUseCase.class))
                .isTrue();
    }
}
