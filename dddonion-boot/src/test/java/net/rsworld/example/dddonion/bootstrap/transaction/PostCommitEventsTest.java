package net.rsworld.example.dddonion.bootstrap.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import net.rsworld.example.dddonion.domain.common.DomainEvent;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class PostCommitEventsTest {

    private record TestEvent() implements DomainEvent {
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

    @Test
    void publishingOutsideTransactionalUseCaseFails() {
        StepVerifier.create(PostCommitEvents.publisher().publish(new TestEvent()))
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("@TransactionalUseCase"))
                .verify();
    }
}
