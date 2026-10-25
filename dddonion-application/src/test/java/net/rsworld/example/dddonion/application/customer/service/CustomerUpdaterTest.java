package net.rsworld.example.dddonion.application.customer.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.rsworld.example.dddonion.domain.common.ConcurrentAggregateUpdateException;
import net.rsworld.example.dddonion.domain.common.Money;
import net.rsworld.example.dddonion.domain.customer.model.Customer;
import net.rsworld.example.dddonion.domain.customer.model.CustomerEmail;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import net.rsworld.example.dddonion.domain.customer.model.CustomerNotFoundException;
import net.rsworld.example.dddonion.domain.customer.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class CustomerUpdaterTest {

    private CustomerRepository customers;
    private CustomerUpdater updater;

    @BeforeEach
    void setUp() {
        customers = mock(CustomerRepository.class);
        updater = new CustomerUpdater(customers);
    }

    private static Customer customer(CustomerId id) {
        return new Customer(id, new CustomerEmail("a@example.com"), Money.of("100"), Money.zero(), 0L);
    }

    @Test
    void appliesChangeAndSaves() {
        var id = CustomerId.newId();
        var loaded = customer(id);
        when(customers.findById(id)).thenReturn(CompletableFuture.completedFuture(Optional.of(loaded)));
        when(customers.save(any())).thenAnswer(inv -> CompletableFuture.completedFuture(inv.getArgument(0)));

        StepVerifier.create(updater.update(id, c -> c.changeCreditLimit(Money.of("5"))))
                .expectNextMatches(c -> c.creditLimit().equals(Money.of("5")))
                .verifyComplete();

        verify(customers).save(loaded);
    }

    @Test
    void failsWithNotFoundForUnknownCustomer() {
        var id = CustomerId.newId();
        when(customers.findById(id)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        StepVerifier.create(updater.update(id, c -> {})).verifyError(CustomerNotFoundException.class);

        verify(customers, never()).save(any());
    }

    @Test
    void retriesWithFreshStateOnConcurrentUpdate() {
        var id = CustomerId.newId();
        var first = customer(id);
        var second = customer(id);
        when(customers.findById(id))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(first)))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(second)));
        when(customers.save(first))
                .thenReturn(
                        CompletableFuture.failedFuture(new ConcurrentAggregateUpdateException("Customer", id.value())));
        when(customers.save(second)).thenReturn(CompletableFuture.completedFuture(second));

        StepVerifier.withVirtualTime(() -> updater.update(id, c -> c.changeCreditLimit(Money.of("7"))))
                .thenAwait(Duration.ofSeconds(1))
                .expectNext(second)
                .verifyComplete();

        verify(customers, times(2)).findById(id);
        org.assertj.core.api.Assertions.assertThat(second.creditLimit()).isEqualTo(Money.of("7"));
    }

    @Test
    void waitsWithBackoffBeforeRetrying() {
        var id = CustomerId.newId();
        var first = customer(id);
        var second = customer(id);
        when(customers.findById(id))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(first)))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(second)));
        when(customers.save(first))
                .thenReturn(
                        CompletableFuture.failedFuture(new ConcurrentAggregateUpdateException("Customer", id.value())));
        when(customers.save(second)).thenReturn(CompletableFuture.completedFuture(second));

        // first backoff is 10 ms with 50 % jitter, so the retry happens between 5 and 15 ms
        StepVerifier.withVirtualTime(() -> updater.update(id, c -> {}))
                .expectSubscription()
                .expectNoEvent(Duration.ofMillis(4))
                .then(() -> verify(customers, times(1)).findById(id))
                .thenAwait(Duration.ofMillis(20))
                .expectNext(second)
                .verifyComplete();

        verify(customers, times(2)).findById(id);
    }

    @Test
    void givesUpAfterThreeAttemptsByDefault() {
        var id = CustomerId.newId();
        var last = alwaysConflicting(id);

        StepVerifier.withVirtualTime(() -> updater.update(id, c -> {}))
                .thenAwait(Duration.ofMinutes(1))
                .verifyErrorMatches(e -> e == last[0]);

        org.assertj.core.api.Assertions.assertThat(CustomerUpdater.DEFAULT_MAX_ATTEMPTS)
                .isEqualTo(3);
        verify(customers, times(3)).save(any());
    }

    @Test
    void givesUpAfterConfiguredAttemptsAndRethrowsOriginalException() {
        var id = CustomerId.newId();
        var last = alwaysConflicting(id);

        StepVerifier.withVirtualTime(() -> updater.update(id, c -> {}, 10))
                .thenAwait(Duration.ofMinutes(1))
                .verifyErrorMatches(e -> e == last[0]);

        verify(customers, times(10)).findById(id);
        verify(customers, times(10)).save(any());
    }

    @Test
    void doesNotRetryOtherErrors() {
        var id = CustomerId.newId();
        when(customers.findById(id)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        StepVerifier.withVirtualTime(() -> updater.update(id, c -> {}, 10))
                .thenAwait(Duration.ofMinutes(1))
                .verifyError(CustomerNotFoundException.class);

        verify(customers, times(1)).findById(id);
    }

    private ConcurrentAggregateUpdateException[] alwaysConflicting(CustomerId id) {
        var last = new ConcurrentAggregateUpdateException[1];
        when(customers.findById(id)).thenAnswer(inv -> CompletableFuture.completedFuture(Optional.of(customer(id))));
        when(customers.save(any())).thenAnswer(inv -> {
            last[0] = new ConcurrentAggregateUpdateException("Customer", id.value());
            return CompletableFuture.failedFuture(last[0]);
        });
        return last;
    }
}
