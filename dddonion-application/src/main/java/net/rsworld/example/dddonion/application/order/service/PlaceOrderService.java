package net.rsworld.example.dddonion.application.order.service;

import lombok.extern.slf4j.Slf4j;
import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.application.event.DomainEventPublisherPort;
import net.rsworld.example.dddonion.application.order.UnknownCustomerException;
import net.rsworld.example.dddonion.application.order.usecase.PlaceOrderUseCase;
import net.rsworld.example.dddonion.architecture.ApplicationComponent;
import net.rsworld.example.dddonion.domain.customer.repository.CustomerRepository;
import net.rsworld.example.dddonion.domain.order.command.PlaceOrderCommand;
import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import net.rsworld.example.dddonion.domain.order.repository.OrderRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Application-Service: orchestrates Domain + Ports. Adaptation from CompletionStage -> Reactor happens here. The
 * customer is only read for the credit check and never saved: one aggregate (the order) changes per command.
 */
@Slf4j
@ApplicationComponent
class PlaceOrderService implements PlaceOrderUseCase {

    private final OrderRepository orders;
    private final CustomerRepository customers;
    private final DomainEventPublisherPort events;

    PlaceOrderService(OrderRepository orders, CustomerRepository customers, DomainEventPublisherPort events) {
        this.orders = orders;
        this.customers = customers;
        this.events = events;
    }

    @Override
    public Mono<OrderId> handle(PlaceOrderCommand cmd, UseCaseMetadata metadata) {
        return Mono.fromCompletionStage(() -> customers.findById(cmd.customerId()))
                .flatMap(Mono::justOrEmpty)
                .switchIfEmpty(Mono.error(() -> new UnknownCustomerException(cmd.customerId())))
                .doOnNext(customer -> customer.ensureCreditFor(cmd.total()))
                .map(customer -> {
                    var order = new Order(cmd.customerId(), cmd.total());
                    order.place();
                    return order;
                })
                .flatMap(this::persistAndPublish)
                // log only the id: keep customer data out of the log
                .doOnNext(id -> log.info("Order {} placed", id.value()));
    }

    private Mono<OrderId> persistAndPublish(Order order) {
        return Mono.fromCompletionStage(() -> orders.save(order))
                .thenMany(Flux.defer(() -> Flux.fromIterable(order.pullEvents())))
                .concatMap(events::publish)
                .then(Mono.just(order.id()));
    }
}
