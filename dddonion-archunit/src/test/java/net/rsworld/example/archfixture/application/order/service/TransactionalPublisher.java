package net.rsworld.example.archfixture.application.order.service;

import net.rsworld.example.dddonion.application.event.DomainEventPublisherPort;
import net.rsworld.example.dddonion.architecture.TransactionalUseCase;
import net.rsworld.example.dddonion.domain.common.DomainEvent;
import reactor.core.publisher.Mono;

/** Fixture: erlaubt, ein Service mit {@code @TransactionalUseCase} darf Domain Events publizieren. */
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
