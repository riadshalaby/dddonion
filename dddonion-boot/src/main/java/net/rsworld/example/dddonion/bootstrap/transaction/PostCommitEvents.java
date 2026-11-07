package net.rsworld.example.dddonion.bootstrap.transaction;

import java.util.ArrayList;
import java.util.List;
import net.rsworld.example.dddonion.application.event.DomainEventPublisherPort;
import net.rsworld.example.dddonion.domain.common.DomainEvent;
import reactor.core.publisher.Mono;

/**
 * Sammelt Domain Events innerhalb einer {@link UseCaseTransaction}, statt sie sofort zu publizieren, damit Listener nur
 * Events zu committetem Zustand sehen. {@link UseCaseTransaction} legt pro Versuch einen Puffer an, reicht ihn über den
 * Reactor-Context nach unten und publiziert seinen Inhalt nach dem Commit.
 */
public final class PostCommitEvents {

    static final String CONTEXT_KEY = PostCommitEvents.class.getName() + ".BUFFER";

    private PostCommitEvents() {}

    /** @return ein Publisher, der das Event puffert; scheitert außerhalb eines {@code @TransactionalUseCase} */
    public static DomainEventPublisherPort publisher() {
        return event -> Mono.deferContextual(ctx -> ctx.<Buffer>getOrEmpty(CONTEXT_KEY)
                .map(buffer -> {
                    buffer.add(event);
                    return Mono.<Void>empty();
                })
                .orElseGet(() -> Mono.error(new IllegalStateException(
                        "Domain events can only be published inside a @TransactionalUseCase"))));
    }

    /** Events eines Versuchs in Publish-Reihenfolge; der Service kann aus verschiedenen Threads publizieren. */
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
