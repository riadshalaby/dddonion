package net.rsworld.example.archfixture.application.order.service;

import java.util.function.Function;
import net.rsworld.example.dddonion.application.event.DomainEventPublisherPort;
import net.rsworld.example.dddonion.domain.common.DomainEvent;
import reactor.core.publisher.Mono;

/** Fixture: ein Service ohne {@code @TransactionalUseCase}, der Domain Events publiziert. */
class NonTransactionalPublisher {
    private final DomainEventPublisherPort events;

    NonTransactionalPublisher(DomainEventPublisherPort events) {
        this.events = events;
    }

    /** Verstoß: direkter publish-Aufruf außerhalb eines transaktionalen Use Cases. */
    Mono<Void> publishesViaCall(DomainEvent event) {
        return events.publish(event);
    }

    /** Verstoß: publish über eine Methodenreferenz. */
    Function<DomainEvent, Mono<Void>> publishesViaMethodReference() {
        return events::publish;
    }
}
