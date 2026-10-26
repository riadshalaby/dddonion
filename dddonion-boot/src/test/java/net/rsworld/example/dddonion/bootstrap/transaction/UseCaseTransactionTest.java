package net.rsworld.example.dddonion.bootstrap.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.rsworld.example.dddonion.application.event.DomainEventPublisherPort;
import net.rsworld.example.dddonion.domain.common.ConcurrentAggregateUpdateException;
import net.rsworld.example.dddonion.domain.common.DomainEvent;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.reactive.AbstractReactiveTransactionManager;
import org.springframework.transaction.reactive.GenericReactiveTransaction;
import org.springframework.transaction.reactive.TransactionSynchronizationManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UseCaseTransactionTest {

    /** Records the transaction lifecycle instead of talking to a database. */
    static class RecordingTransactionManager extends AbstractReactiveTransactionManager {
        final List<String> events = new ArrayList<>();

        @Override
        protected Object doGetTransaction(TransactionSynchronizationManager synchronizationManager) {
            return new Object();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return false;
        }

        @Override
        protected Mono<Void> doBegin(
                TransactionSynchronizationManager synchronizationManager,
                Object transaction,
                TransactionDefinition definition) {
            return Mono.fromRunnable(() -> events.add("begin"));
        }

        @Override
        protected Mono<Void> doCommit(
                TransactionSynchronizationManager synchronizationManager, GenericReactiveTransaction status) {
            return Mono.fromRunnable(() -> events.add("commit"));
        }

        @Override
        protected Mono<Void> doRollback(
                TransactionSynchronizationManager synchronizationManager, GenericReactiveTransaction status) {
            return Mono.fromRunnable(() -> events.add("rollback"));
        }
    }

    private record TestEvent(String name) implements DomainEvent {
        @Override
        public String aggregateId() {
            return "1";
        }

        @Override
        public long sequence() {
            return 1;
        }

        @Override
        public Instant occurredAt() {
            return Instant.EPOCH;
        }

        @Override
        public String type() {
            return "Test";
        }
    }

    private final RecordingTransactionManager manager = new RecordingTransactionManager();
    private final UseCaseTransaction transaction = new UseCaseTransaction(
            TransactionalOperator.create(manager), event -> manager.events.add(((TestEvent) event).name()));
    private final AtomicInteger calls = new AtomicInteger();

    /** Counts subscriptions, like a service that is re-invoked per attempt. */
    private Mono<?> counting(java.util.function.Supplier<Mono<?>> body) {
        return Mono.defer(() -> {
            calls.incrementAndGet();
            return body.get();
        });
    }

    @Test
    void commitsOnSuccess() {
        StepVerifier.create(transaction.apply(Mono.just("x")))
                .expectNextMatches("x"::equals)
                .verifyComplete();

        assertThat(manager.events).containsExactly("begin", "commit");
    }

    @Test
    void rollsBackOnErrorWithoutRetry() {
        var failure = new IllegalStateException("boom");

        StepVerifier.create(transaction.apply(counting(() -> Mono.error(failure))))
                .expectErrorSatisfies(e -> assertThat(e).isSameAs(failure))
                .verify();

        assertThat(manager.events).containsExactly("begin", "rollback");
        assertThat(calls).hasValue(1);
    }

    @Test
    void emptyResultCommits() {
        StepVerifier.create(transaction.apply(Mono.empty())).verifyComplete();

        assertThat(manager.events).containsExactly("begin", "commit");
    }

    @Test
    void retriesConflictInFreshTransaction() {
        Mono<?> invocation = counting(() -> calls.get() == 1
                ? Mono.error(new ConcurrentAggregateUpdateException("Customer", "1"))
                : Mono.just("ok"));

        StepVerifier.withVirtualTime(() -> transaction.apply(invocation))
                .thenAwait(Duration.ofSeconds(1))
                .expectNextMatches("ok"::equals)
                .verifyComplete();

        assertThat(manager.events).containsExactly("begin", "rollback", "begin", "commit");
        assertThat(calls).hasValue(2);
    }

    @Test
    void givesUpAfterThreeAttemptsAndRethrowsOriginal() {
        List<ConcurrentAggregateUpdateException> failures = new ArrayList<>();
        Mono<?> invocation = counting(() -> {
            var failure = new ConcurrentAggregateUpdateException("Customer", "1");
            failures.add(failure);
            return Mono.error(failure);
        });

        StepVerifier.withVirtualTime(() -> transaction.apply(invocation))
                .thenAwait(Duration.ofSeconds(1))
                .expectErrorSatisfies(e -> assertThat(e).isSameAs(failures.get(2)))
                .verify();

        assertThat(calls).hasValue(3);
        assertThat(failures).hasSize(3);
    }

    private final DomainEventPublisherPort publisher = PostCommitEvents.publisher();

    private Mono<Void> publish(String name) {
        return publisher.publish(new TestEvent(name));
    }

    @Test
    void publishesBufferedEventsAfterCommitInOrder() {
        Mono<?> invocation = publish("e1").then(publish("e2")).thenReturn("x");

        StepVerifier.create(transaction.apply(invocation))
                .expectNextMatches("x"::equals)
                .verifyComplete();

        assertThat(manager.events).containsExactly("begin", "commit", "e1", "e2");
    }

    @Test
    void discardsEventsOnRollback() {
        Mono<?> invocation = publish("e1").then(Mono.error(new IllegalStateException("boom")));

        StepVerifier.create(transaction.apply(invocation))
                .expectError(IllegalStateException.class)
                .verify();

        assertThat(manager.events).containsExactly("begin", "rollback");
    }

    @Test
    void publishesOnlyEventsOfTheSuccessfulAttempt() {
        Mono<?> invocation = counting(() -> calls.get() == 1
                ? publish("e1").then(Mono.error(new ConcurrentAggregateUpdateException("Customer", "1")))
                : publish("e2").thenReturn("ok"));

        StepVerifier.withVirtualTime(() -> transaction.apply(invocation))
                .thenAwait(Duration.ofSeconds(1))
                .expectNextMatches("ok"::equals)
                .verifyComplete();

        assertThat(manager.events).containsExactly("begin", "rollback", "begin", "commit", "e2");
    }

    @Test
    void publishesEventsOfEmptyResult() {
        StepVerifier.create(transaction.apply(publish("e1"))).verifyComplete();

        assertThat(manager.events).containsExactly("begin", "commit", "e1");
    }

    @Test
    void listenerFailureDoesNotAffectCommittedResultOrLaterEvents() {
        var failingTransaction = new UseCaseTransaction(TransactionalOperator.create(manager), event -> {
            String name = ((TestEvent) event).name();
            if (name.equals("e1")) {
                throw new IllegalStateException("listener rejected " + name);
            }
            manager.events.add(name);
        });
        Mono<?> invocation = counting(() -> publish("e1").then(publish("e2")).thenReturn("x"));

        StepVerifier.create(failingTransaction.apply(invocation))
                .expectNextMatches("x"::equals)
                .verifyComplete();

        assertThat(manager.events).containsExactly("begin", "commit", "e2");
        assertThat(calls).hasValue(1);
    }
}
