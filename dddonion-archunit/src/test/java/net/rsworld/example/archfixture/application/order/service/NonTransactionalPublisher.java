package net.rsworld.example.archfixture.application.order.service;

import java.util.function.Function;
import net.rsworld.example.dddonion.application.event.DomainEventPublisherPort;
import net.rsworld.example.dddonion.domain.common.DomainEvent;
import reactor.core.publisher.Mono;

/** Fixture: a service without {@code @TransactionalUseCase} that publishes domain events. */
class NonTransactionalPublisher {
    private final DomainEventPublisherPort events;

    NonTransactionalPublisher(DomainEventPublisherPort events) {
        this.events = events;
    }

    /** Violation: direct publish call outside a transactional use case. */
    Mono<Void> publishesViaCall(DomainEvent event) {
        return events.publish(event);
    }

    /** Violation: publish through a method reference. */
    Function<DomainEvent, Mono<Void>> publishesViaMethodReference() {
        return events::publish;
    }
}
