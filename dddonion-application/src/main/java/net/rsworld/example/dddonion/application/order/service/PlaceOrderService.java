package net.rsworld.example.dddonion.application.order.service;

import lombok.extern.slf4j.Slf4j;
import net.rsworld.example.dddonion.application.order.usecase.PlaceOrderUseCase;
import net.rsworld.example.dddonion.domain.order.command.PlaceOrderCommand;
import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import net.rsworld.example.dddonion.domain.order.repository.OrderRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/** Application-Service: orchestrates Domain + Ports. Adaptation from CompletionStage -> Reactor happens here. */
@Slf4j
public class PlaceOrderService implements PlaceOrderUseCase {

    private final OrderRepository orders;
    private final ApplicationEventPublisher events;

    public PlaceOrderService(OrderRepository orders, ApplicationEventPublisher events) {
        this.orders = orders;
        this.events = events;
    }

    @Override
    @Transactional
    public Mono<OrderId> handle(PlaceOrderCommand cmd) {
        var order = new Order(cmd.customerEmail(), cmd.total());
        order.place();

        return Mono.fromCompletionStage(orders.save(order))
                .doOnSuccess(saved -> order.pullEvents().forEach(events::publishEvent))
                .then(Mono.just(order.id()))
                .doOnNext(id -> log.info("DomainEvent with order id ({}) processed: {}", id.value(), cmd));
    }
}
