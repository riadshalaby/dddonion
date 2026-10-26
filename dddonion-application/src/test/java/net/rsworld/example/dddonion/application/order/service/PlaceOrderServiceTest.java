package net.rsworld.example.dddonion.application.order.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import net.rsworld.example.dddonion.application.context.ActorType;
import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.application.event.DomainEventPublisherPort;
import net.rsworld.example.dddonion.application.order.UnknownCustomerException;
import net.rsworld.example.dddonion.domain.common.DomainEvent;
import net.rsworld.example.dddonion.domain.common.Money;
import net.rsworld.example.dddonion.domain.customer.model.CreditLimitExceededException;
import net.rsworld.example.dddonion.domain.customer.model.Customer;
import net.rsworld.example.dddonion.domain.customer.model.CustomerEmail;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import net.rsworld.example.dddonion.domain.customer.repository.CustomerRepository;
import net.rsworld.example.dddonion.domain.order.command.PlaceOrderCommand;
import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.domain.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.publisher.PublisherProbe;

class PlaceOrderServiceTest {

    private static final UseCaseMetadata META = new UseCaseMetadata(ActorType.PRIVATE_CUSTOMER, "test", "corr-1");
    private static final CustomerId CUSTOMER_ID = CustomerId.newId();

    private OrderRepository orders;
    private CustomerRepository customers;
    private DomainEventPublisherPort publisher;
    private PlaceOrderService service;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        customers = mock(CustomerRepository.class);
        publisher = mock(DomainEventPublisherPort.class);
        service = new PlaceOrderService(orders, customers, publisher);
    }

    private void customerExists(String limit, String open) {
        var customer = new Customer(
                CUSTOMER_ID, new CustomerEmail("john.doe@example.com"), Money.of(limit), Money.of(open), 0L);
        when(customers.findById(CUSTOMER_ID)).thenReturn(Mono.just(customer));
    }

    private static PlaceOrderCommand cmd(String total) {
        return new PlaceOrderCommand(CUSTOMER_ID, new Money(new BigDecimal(total)));
    }

    private void saveSucceeds() {
        when(orders.save(any(Order.class))).thenAnswer(inv -> Mono.just((Order) inv.getArgument(0)));
        when(publisher.publish(any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Legt die Order an, wenn das Kreditlimit reicht, und publiziert das Event nach dem Persistieren")
    void placesOrderWithinCreditLimit() {
        customerExists("1000.00", "0.00");
        saveSucceeds();

        StepVerifier.create(service.handle(cmd("42.50"), META))
                .expectNextMatches(
                        id -> id != null && id.value() != null && !id.value().isBlank())
                .verifyComplete();

        ArgumentCaptor<DomainEvent> published = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, times(1)).publish(published.capture());
        verify(orders, times(1)).save(any(Order.class));
    }

    @Test
    @DisplayName("Akzeptiert eine Order, die genau das verbleibende Kreditlimit ausschöpft")
    void acceptsOrderExactlyAtRemainingCredit() {
        customerExists("100.00", "40.00");
        saveSucceeds();

        StepVerifier.create(service.handle(cmd("60.00"), META))
                .expectNextCount(1)
                .verifyComplete();

        verify(orders, times(1)).save(any(Order.class));
    }

    @Test
    @DisplayName("Lehnt einen unbekannten Customer ab, ohne zu speichern oder zu publizieren")
    void rejectsUnknownCustomer() {
        when(customers.findById(CUSTOMER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.handle(cmd("10.00"), META))
                .expectError(UnknownCustomerException.class)
                .verify();

        verifyNoInteractions(orders, publisher);
    }

    @Test
    @DisplayName("Lehnt eine Order über dem Kreditlimit ab, ohne zu speichern oder zu publizieren")
    void rejectsOrderAboveCreditLimit() {
        customerExists("100.00", "40.00");

        StepVerifier.create(service.handle(cmd("60.01"), META))
                .expectError(CreditLimitExceededException.class)
                .verify();

        verifyNoInteractions(orders, publisher);
    }

    @Test
    @DisplayName("Speichert den Customer nie (ein Aggregate pro Command)")
    void doesNotModifyCustomer() {
        customerExists("1000.00", "0.00");
        saveSucceeds();

        StepVerifier.create(service.handle(cmd("10.00"), META))
                .expectNextCount(1)
                .verifyComplete();

        verify(customers, never()).save(any());
    }

    @Test
    @DisplayName("Publiziert keine Domain-Events, wenn das Persistieren fehlschlägt")
    void doesNotPublishEventsWhenPersistenceFails() {
        customerExists("1000.00", "0.00");
        RuntimeException boom = new RuntimeException("db unavailable");
        when(orders.save(any(Order.class))).thenReturn(Mono.error(boom));

        StepVerifier.create(service.handle(cmd("42.50"), META))
                .expectErrorMatches(err -> err == boom)
                .verify();

        verify(orders, times(1)).save(any(Order.class));
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("Liest den Customer erst beim Subscribe")
    void loadsCustomerLazily() {
        PublisherProbe<Customer> lookup = PublisherProbe.empty();
        when(customers.findById(CUSTOMER_ID)).thenReturn(lookup.mono());

        service.handle(cmd("1.00"), META);

        lookup.assertWasNotSubscribed();
        verifyNoInteractions(orders, publisher);
    }
}
