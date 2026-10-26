package net.rsworld.example.dddonion.application.order.service;

import lombok.extern.slf4j.Slf4j;
import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.application.event.DomainEventPublisherPort;
import net.rsworld.example.dddonion.application.order.UnknownCustomerException;
import net.rsworld.example.dddonion.application.order.usecase.PlaceOrderUseCase;
import net.rsworld.example.dddonion.architecture.ApplicationComponent;
import net.rsworld.example.dddonion.architecture.TransactionalUseCase;
import net.rsworld.example.dddonion.domain.customer.repository.CustomerRepository;
import net.rsworld.example.dddonion.domain.order.command.PlaceOrderCommand;
import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import net.rsworld.example.dddonion.domain.order.repository.OrderRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Application-Service: orchestrates Domain + Ports. Runs as one transaction: the credit is reserved on the customer and
 * the order is created. The customer is saved first so that a version conflict surfaces before the order insert; the
 * pipeline then retries the whole chain. The order is built inside the chain so that every attempt gets a new id.
 */
@Slf4j
@TransactionalUseCase
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
        return customers
                .findById(cmd.customerId())
                .switchIfEmpty(Mono.error(() -> new UnknownCustomerException(cmd.customerId())))
                .doOnNext(customer -> customer.reserveCredit(cmd.total()))
                .flatMap(customer -> {
                    var order = new Order(cmd.customerId(), cmd.total());
                    order.place();
                    return customers
                            .save(customer)
                            .then(Mono.defer(() -> orders.save(order)))
                            .then(publishEvents(order));
                })
                // log only the id: keep customer data out of the log; the commit is not observable here
                .doOnNext(id -> log.info("Order {} saved, awaiting commit", id.value()));
    }

    private Mono<OrderId> publishEvents(Order order) {
        return Flux.defer(() -> Flux.fromIterable(order.pullEvents()))
                .concatMap(events::publish)
                .then(Mono.just(order.id()));
    }
}
