package net.rsworld.example.dddonion.bootstrap.transaction;

import java.time.Duration;
import java.util.function.UnaryOperator;
import net.rsworld.example.dddonion.domain.common.ConcurrentAggregateUpdateException;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Runs a use case invocation in a transaction and retries it on {@link ConcurrentAggregateUpdateException}.
 *
 * <p>The retry wraps the transaction, so every attempt re-invokes the service (it must load its aggregates anew) in a
 * fresh transaction; the failed attempt is rolled back first. When the attempts are used up, the original exception
 * reaches the caller.
 */
public final class UseCaseTransaction implements UnaryOperator<Mono<?>> {

    static final int MAX_ATTEMPTS = 3;
    static final Duration FIRST_BACKOFF = Duration.ofMillis(10);
    static final Duration MAX_BACKOFF = Duration.ofMillis(200);
    static final double JITTER = 0.5;

    private final TransactionalOperator tx;

    public UseCaseTransaction(TransactionalOperator tx) {
        this.tx = tx;
    }

    /** @param invocation must call the service anew on every subscription */
    @Override
    public Mono<?> apply(Mono<?> invocation) {
        return tx.transactional(invocation)
                .retryWhen(Retry.backoff(MAX_ATTEMPTS - 1, FIRST_BACKOFF)
                        .maxBackoff(MAX_BACKOFF)
                        .jitter(JITTER)
                        .filter(ConcurrentAggregateUpdateException.class::isInstance)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
    }
}
