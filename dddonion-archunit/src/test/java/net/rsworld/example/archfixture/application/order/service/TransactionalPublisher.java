package net.rsworld.example.archfixture.application.order.service;

import net.rsworld.example.dddonion.application.event.DomainEventPublisherPort;
import net.rsworld.example.dddonion.architecture.TransactionalUseCase;
import net.rsworld.example.dddonion.domain.common.DomainEvent;
import reactor.core.publisher.Mono;

/** Fixture: allowed, a service annotated with {@code @TransactionalUseCase} may publish domain events. */
@TransactionalUseCase
class TransactionalPublisher {
    private final DomainEventPublisherPort events;

    TransactionalPublisher(DomainEventPublisherPort events) {
        this.events = events;
    }

    Mono<Void> publishes(DomainEvent event) {
        return events.publish(event);
    }
}
