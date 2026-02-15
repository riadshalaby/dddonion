package net.rsworld.example.dddonion.application.event;

import net.rsworld.example.dddonion.domain.common.DomainEvent;
import reactor.core.publisher.Mono;

public interface DomainEventPublisherPort {
    Mono<Void> publish(DomainEvent event);
}
