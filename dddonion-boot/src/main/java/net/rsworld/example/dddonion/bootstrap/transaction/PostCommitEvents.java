package net.rsworld.example.dddonion.bootstrap.transaction;

import java.util.ArrayList;
import java.util.List;
import net.rsworld.example.dddonion.application.event.DomainEventPublisherPort;
import net.rsworld.example.dddonion.domain.common.DomainEvent;
import reactor.core.publisher.Mono;

/**
 * Collects domain events inside a {@link UseCaseTransaction} instead of publishing them right away, so listeners only
 * see events of committed state. {@link UseCaseTransaction} creates one buffer per attempt, hands it down through the
 * Reactor context and publishes its content after the commit.
 */
public final class PostCommitEvents {

    static final String CONTEXT_KEY = PostCommitEvents.class.getName() + ".BUFFER";

    private PostCommitEvents() {}

    /** @return a publisher that buffers the event; fails outside a {@code @TransactionalUseCase} */
    public static DomainEventPublisherPort publisher() {
        return event -> Mono.deferContextual(ctx -> ctx.<Buffer>getOrEmpty(CONTEXT_KEY)
                .map(buffer -> {
                    buffer.add(event);
                    return Mono.<Void>empty();
                })
                .orElseGet(() -> Mono.error(new IllegalStateException(
                        "Domain events can only be published inside a @TransactionalUseCase"))));
    }

    /** Events of one attempt in publish order; the service may publish from different threads. */
    static final class Buffer {
        private final List<DomainEvent> events = new ArrayList<>();

        synchronized void add(DomainEvent event) {
            events.add(event);
        }

        synchronized List<DomainEvent> snapshot() {
            return List.copyOf(events);
        }
    }
}
