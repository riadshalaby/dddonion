package net.rsworld.example.dddonion.bootstrap.transaction;

import java.time.Duration;
import java.util.Optional;
import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;
import net.rsworld.example.dddonion.domain.common.ConcurrentAggregateUpdateException;
import net.rsworld.example.dddonion.domain.common.DomainEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Führt einen Use-Case-Aufruf in einer Transaktion aus und wiederholt ihn bei
 * {@link ConcurrentAggregateUpdateException}.
 *
 * <p>Der Retry umschließt die Transaktion, daher ruft jeder Versuch den Service neu auf (er muss seine Aggregates neu
 * laden), in einer frischen Transaktion; der gescheiterte Versuch wird vorher zurückgerollt. Sind die Versuche
 * aufgebraucht, erreicht die ursprüngliche Exception den Aufrufer.
 *
 * <p>Domain Events, die über {@link PostCommitEvents#publisher()} publiziert werden, werden pro Versuch gepuffert und
 * nach dem Commit publiziert, bevor das Ergebnis emittiert wird. Ein gescheiterter Versuch publiziert nichts. Ein
 * Listener, der wirft, wird geloggt und übersprungen. Das Publizieren läuft in derselben Kette, daher ist der
 * Reactor-Context (Correlation-ID im MDC) für synchrone Listener noch wiederhergestellt.
 */
@Slf4j
public final class UseCaseTransaction implements UnaryOperator<Mono<?>> {

    static final int MAX_ATTEMPTS = 3;
    static final Duration FIRST_BACKOFF = Duration.ofMillis(10);
    static final Duration MAX_BACKOFF = Duration.ofMillis(200);
    static final double JITTER = 0.5;

    private final TransactionalOperator tx;
    private final ApplicationEventPublisher events;

    public UseCaseTransaction(TransactionalOperator tx, ApplicationEventPublisher events) {
        this.tx = tx;
        this.events = events;
    }

    /** @param invocation muss den Service bei jeder Subscription neu aufrufen */
    @Override
    public Mono<?> apply(Mono<?> invocation) {
        return Mono.defer(() -> attempt(invocation))
                .retryWhen(Retry.backoff(MAX_ATTEMPTS - 1, FIRST_BACKOFF)
                        .maxBackoff(MAX_BACKOFF)
                        .jitter(JITTER)
                        .filter(ConcurrentAggregateUpdateException.class::isInstance)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .flatMap(committed -> Flux.fromIterable(committed.buffer().snapshot())
                        .doOnNext(this::publishIsolated)
                        .then(Mono.justOrEmpty(committed.result())));
    }

    /**
     * Der Use Case ist committet, daher darf ein scheiternder Listener weder den Aufrufer erreichen noch die späteren
     * Events aufhalten.
     */
    private void publishIsolated(DomainEvent event) {
        try {
            events.publishEvent(event);
        } catch (RuntimeException e) {
            log.error("Listener failed for committed domain event {}", event.type(), e);
        }
    }

    /** Ein Versuch mit eigenem Event-Puffer; schließt nach dem Commit ab, mit leerem Ergebnis, falls es keines gibt. */
    private Mono<Committed> attempt(Mono<?> invocation) {
        PostCommitEvents.Buffer buffer = new PostCommitEvents.Buffer();
        // transactional(Mono) ist execute(..).singleOrEmpty(): onNext wird zurückgehalten, bis der Commit abgeschlossen
        // ist
        return tx.transactional(invocation)
                .<Object>map(Object.class::cast)
                .map(result -> new Committed(buffer, Optional.of(result)))
                .defaultIfEmpty(new Committed(buffer, Optional.empty()))
                .contextWrite(ctx -> ctx.put(PostCommitEvents.CONTEXT_KEY, buffer));
    }

    private record Committed(PostCommitEvents.Buffer buffer, Optional<Object> result) {}
}
