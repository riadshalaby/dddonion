package net.rsworld.example.dddonion.application.outbox;

import net.rsworld.example.dddonion.domain.common.DomainEvent;
import reactor.core.publisher.Mono;

public interface OutboxPort {
    Mono<Void> save(DomainEvent event);
}
