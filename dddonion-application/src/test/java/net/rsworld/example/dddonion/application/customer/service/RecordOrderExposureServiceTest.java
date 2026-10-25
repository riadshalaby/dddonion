package net.rsworld.example.dddonion.application.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.rsworld.example.dddonion.application.context.ActorType;
import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.domain.common.ConcurrentAggregateUpdateException;
import net.rsworld.example.dddonion.domain.common.Money;
import net.rsworld.example.dddonion.domain.customer.command.RecordOrderExposureCommand;
import net.rsworld.example.dddonion.domain.customer.model.Customer;
import net.rsworld.example.dddonion.domain.customer.model.CustomerEmail;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import net.rsworld.example.dddonion.domain.customer.repository.CustomerRepository;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.test.StepVerifier;

class RecordOrderExposureServiceTest {

    @Test
    void addsOrderTotalToOpenAmountViaUpdater() {
        var customers = mock(CustomerRepository.class);
        var id = CustomerId.newId();
        var loaded = new Customer(id, new CustomerEmail("a@example.com"), Money.of("100"), Money.of("10"), 0L);
        when(customers.findById(id)).thenReturn(CompletableFuture.completedFuture(Optional.of(loaded)));
        when(customers.save(any())).thenAnswer(inv -> CompletableFuture.completedFuture(inv.getArgument(0)));
        var service = new RecordOrderExposureService(new CustomerUpdater(customers));
        var meta = new UseCaseMetadata(ActorType.UNKNOWN, "event", "corr-1");

        StepVerifier.create(service.handle(new RecordOrderExposureCommand(id, OrderId.newId(), Money.of("60")), meta))
                .verifyComplete();

        var captor = ArgumentCaptor.forClass(Customer.class);
        verify(customers).save(captor.capture());
        assertThat(captor.getValue().openAmount()).isEqualTo(Money.of("70"));
    }

    @Test
    void retriesConcurrentUpdatesUpToTenAttempts() {
        var customers = mock(CustomerRepository.class);
        var id = CustomerId.newId();
        when(customers.findById(id))
                .thenAnswer(inv -> CompletableFuture.completedFuture(Optional.of(
                        new Customer(id, new CustomerEmail("a@example.com"), Money.of("100"), Money.zero(), 0L))));
        when(customers.save(any()))
                .thenAnswer(inv ->
                        CompletableFuture.failedFuture(new ConcurrentAggregateUpdateException("Customer", id.value())));
        var service = new RecordOrderExposureService(new CustomerUpdater(customers));
        var meta = new UseCaseMetadata(ActorType.UNKNOWN, "event", "corr-1");

        StepVerifier.withVirtualTime(
                        () -> service.handle(new RecordOrderExposureCommand(id, OrderId.newId(), Money.of("1")), meta))
                .thenAwait(Duration.ofMinutes(1))
                .verifyError(ConcurrentAggregateUpdateException.class);

        assertThat(RecordOrderExposureService.EVENT_MAX_ATTEMPTS).isEqualTo(10);
        verify(customers, times(10)).save(any());
    }
}
