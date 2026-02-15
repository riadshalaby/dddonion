package net.rsworld.example.dddonion.application.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import net.rsworld.example.dddonion.application.event.DomainEventPublisherPort;
import net.rsworld.example.dddonion.application.order.service.PlaceOrderService;
import net.rsworld.example.dddonion.domain.common.DomainEvent;
import net.rsworld.example.dddonion.domain.order.command.PlaceOrderCommand;
import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import net.rsworld.example.dddonion.domain.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PlaceOrderServiceTest {

    @Test
    @DisplayName("Publiziert Domain-Events über den Publisher-Port nach erfolgreichem Persistieren")
    void publishesDomainEventsViaPortAfterPersistence() {
        // arrange
        OrderRepository orders = mock(OrderRepository.class);
        DomainEventPublisherPort publisher = mock(DomainEventPublisherPort.class);

        when(orders.save(any(Order.class)))
                .thenAnswer(inv -> CompletableFuture.completedFuture((Order) inv.getArgument(0)));
        when(publisher.publish(any())).thenReturn(Mono.empty());

        PlaceOrderService service = new PlaceOrderService(orders, publisher);

        PlaceOrderCommand cmd = new PlaceOrderCommand("john.doe@example.com", new BigDecimal("42.50"));

        // act
        Mono<OrderId> result = service.handle(cmd);

        // assert
        StepVerifier.create(result)
                .expectNextMatches(
                        id -> id != null && id.value() != null && !id.value().isBlank())
                .verifyComplete();

        // capture published events to assert type/count explicitly
        ArgumentCaptor<DomainEvent> published = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, times(1)).publish(published.capture());
        DomainEvent event = published.getValue();
        assert event != null;

        verify(orders, times(1)).save(any(Order.class));
        verifyNoMoreInteractions(orders, publisher);
    }

    @Test
    @DisplayName("Publiziert keine Domain-Events, wenn das Persistieren fehlschlägt")
    void doesNotPublishEventsWhenPersistenceFails() {
        OrderRepository orders = mock(OrderRepository.class);
        DomainEventPublisherPort publisher = mock(DomainEventPublisherPort.class);
        RuntimeException boom = new RuntimeException("db unavailable");

        when(orders.save(any(Order.class))).thenReturn(CompletableFuture.failedFuture(boom));

        PlaceOrderService service = new PlaceOrderService(orders, publisher);
        PlaceOrderCommand cmd = new PlaceOrderCommand("john.doe@example.com", new BigDecimal("42.50"));

        StepVerifier.create(service.handle(cmd))
                .expectErrorMatches(err -> err == boom)
                .verify();

        verify(orders, times(1)).save(any(Order.class));
        verifyNoInteractions(publisher);
        verifyNoMoreInteractions(orders);
    }
}
