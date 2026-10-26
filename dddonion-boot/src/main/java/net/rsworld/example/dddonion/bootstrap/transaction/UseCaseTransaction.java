package net.rsworld.example.dddonion.bootstrap.transaction;

import java.time.Duration;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.rsworld.example.dddonion.domain.common.ConcurrentAggregateUpdateException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Runs a use case invocation in a transaction and retries it on {@link ConcurrentAggregateUpdateException}.
 *
 * <p>The retry wraps the transaction, so every attempt re-invokes the service (it must load its aggregates anew) in a
 * fresh transaction; the failed attempt is rolled back first. When the attempts are used up, the original exception
 * reaches the caller.
 *
 * <p>Domain events published through {@link PostCommitEvents#publisher()} are buffered per attempt and published after
 * the commit, before the result is emitted. A failed attempt publishes nothing. The publishing runs in the same chain,
 * so the Reactor context (correlation id in the MDC) is still restored for synchronous listeners.
 */
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

    /** @param invocation must call the service anew on every subscription */
    @Override
    public Mono<?> apply(Mono<?> invocation) {
        return Mono.defer(() -> attempt(invocation))
                .retryWhen(Retry.backoff(MAX_ATTEMPTS - 1, FIRST_BACKOFF)
                        .maxBackoff(MAX_BACKOFF)
                        .jitter(JITTER)
                        .filter(ConcurrentAggregateUpdateException.class::isInstance)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .flatMap(committed -> Flux.fromIterable(committed.buffer().snapshot())
                        .doOnNext(events::publishEvent)
                        .then(Mono.justOrEmpty(committed.result())));
    }

    /** One attempt with its own event buffer; completes after the commit, with an empty result if there is none. */
    private Mono<Committed> attempt(Mono<?> invocation) {
        PostCommitEvents.Buffer buffer = new PostCommitEvents.Buffer();
        return tx.transactional(invocation)
                .<Object>map(Object.class::cast)
                .map(result -> new Committed(buffer, Optional.of(result)))
                .defaultIfEmpty(new Committed(buffer, Optional.empty()))
                .contextWrite(ctx -> ctx.put(PostCommitEvents.CONTEXT_KEY, buffer));
    }

    private record Committed(PostCommitEvents.Buffer buffer, Optional<Object> result) {}
}
