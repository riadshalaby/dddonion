package net.rsworld.example.dddonion.application.customer.service;

import java.time.Duration;
import java.util.function.Consumer;
import net.rsworld.example.dddonion.architecture.ApplicationComponent;
import net.rsworld.example.dddonion.domain.common.ConcurrentAggregateUpdateException;
import net.rsworld.example.dddonion.domain.customer.model.Customer;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import net.rsworld.example.dddonion.domain.customer.model.CustomerNotFoundException;
import net.rsworld.example.dddonion.domain.customer.repository.CustomerRepository;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Shared load-modify-save step for use cases changing an existing customer. On an optimistic-locking conflict the whole
 * step is repeated with a fresh load after an exponential backoff with jitter, so concurrent writers spread out instead
 * of colliding again. A conflict means another update committed, so with {@code n} attempts up to {@code n} concurrent
 * updates cannot lose each other. When the attempts are exhausted the last conflict is rethrown.
 */
@ApplicationComponent
final class CustomerUpdater {

    /** Attempts for callers that can answer a conflict to a client (REST: exhausted retries become 409). */
    static final int DEFAULT_MAX_ATTEMPTS = 3;

    static final Duration FIRST_BACKOFF = Duration.ofMillis(10);
    static final Duration MAX_BACKOFF = Duration.ofMillis(200);
    static final double JITTER = 0.5;

    private final CustomerRepository customers;

    CustomerUpdater(CustomerRepository customers) {
        this.customers = customers;
    }

    Mono<Customer> update(CustomerId id, Consumer<Customer> change) {
        return update(id, change, DEFAULT_MAX_ATTEMPTS);
    }

    Mono<Customer> update(CustomerId id, Consumer<Customer> change, int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1: " + maxAttempts);
        }
        return Mono.defer(() -> loadModifySave(id, change))
                .retryWhen(Retry.backoff(maxAttempts - 1, FIRST_BACKOFF)
                        .maxBackoff(MAX_BACKOFF)
                        .jitter(JITTER)
                        .filter(ConcurrentAggregateUpdateException.class::isInstance)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
    }

    private Mono<Customer> loadModifySave(CustomerId id, Consumer<Customer> change) {
        return Mono.fromCompletionStage(() -> customers.findById(id))
                .flatMap(found -> found.map(Mono::just).orElseGet(Mono::empty))
                .switchIfEmpty(Mono.error(() -> new CustomerNotFoundException(id)))
                .doOnNext(change)
                .flatMap(customer -> Mono.fromCompletionStage(() -> customers.save(customer)));
    }
}
