package net.rsworld.example.dddonion.application.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import net.rsworld.example.dddonion.application.order.service.PlaceOrderService;
import net.rsworld.example.dddonion.application.outbox.OutboxPort;
import net.rsworld.example.dddonion.domain.common.DomainEvent;
import net.rsworld.example.dddonion.domain.order.command.PlaceOrderCommand;
import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import net.rsworld.example.dddonion.domain.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PlaceOrderServiceTest {

    @Test
    @DisplayName("Publiziert Domain-Events und persistiert diese parallel im Outbox-Port")
    void publishesDomainEventsToSpringAndPersistsToOutbox() {
        // arrange
        OrderRepository orders = mock(OrderRepository.class);
        OutboxPort outbox = mock(OutboxPort.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

        when(orders.save(any(Order.class)))
                .thenAnswer(inv -> CompletableFuture.completedFuture((Order) inv.getArgument(0)));
        when(outbox.save(any())).thenReturn(Mono.empty());

        PlaceOrderService service = new PlaceOrderService(orders, outbox, publisher);

        PlaceOrderCommand cmd = new PlaceOrderCommand("john.doe@example.com", new BigDecimal("42.50"));

        // act
        Mono<OrderId> result = service.handle(cmd);

        // assert
        StepVerifier.create(result)
                .expectNextMatches(
                        id -> id != null && id.value() != null && !id.value().isBlank())
                .verifyComplete();

        // capture published events to assert type/count explicitly
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(publisher, times(1)).publishEvent(published.capture());
        Object event = published.getValue();
        // ensure it is a DomainEvent (record type detail not required here)
        assert event instanceof DomainEvent;

        verify(orders, times(1)).save(any(Order.class));
        verify(outbox, times(1)).save(any());
        verifyNoMoreInteractions(orders, outbox, publisher);
    }
}
