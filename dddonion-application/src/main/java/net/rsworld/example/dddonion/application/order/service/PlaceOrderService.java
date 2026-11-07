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
 * Application Service: orchestriert Domain und Ports. Läuft als eine Transaktion: Der Kredit wird beim Customer
 * reserviert und die Order angelegt. Der Customer wird zuerst gespeichert, damit ein Versionskonflikt vor dem Insert
 * der Order auffällt; die Pipeline wiederholt dann die ganze Kette. Die Order wird innerhalb der Kette gebaut, damit
 * jeder Versuch eine neue Id bekommt.
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
                // nur die Id loggen: Customer-Daten bleiben aus dem Log; der Commit ist hier nicht beobachtbar
                .doOnNext(id -> log.info("Order {} saved, awaiting commit", id.value()));
    }

    private Mono<OrderId> publishEvents(Order order) {
        return Flux.defer(() -> Flux.fromIterable(order.pullEvents()))
                .concatMap(events::publish)
                .then(Mono.just(order.id()));
    }
}
