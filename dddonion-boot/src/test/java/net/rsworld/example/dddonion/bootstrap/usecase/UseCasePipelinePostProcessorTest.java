package net.rsworld.example.dddonion.bootstrap.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.observation.ObservationRegistry;
import java.util.Map;
import net.rsworld.example.dddonion.application.context.ActorType;
import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.architecture.TransactionalUseCase;
import net.rsworld.example.dddonion.architecture.UseCase;
import net.rsworld.example.dddonion.bootstrap.observability.UseCaseObservability;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

class UseCasePipelinePostProcessorTest {

    @UseCase(name = "thing.place", kind = UseCase.Kind.COMMAND)
    public interface PlaceThingUseCase {
        Mono<String> handle(String cmd, UseCaseMetadata metadata);
    }

    @UseCase(name = "thing.get", kind = UseCase.Kind.QUERY)
    interface GetThingUseCase {
        Mono<String> find(String id, UseCaseMetadata metadata);
    }

    @UseCase(name = "thing.place", kind = UseCase.Kind.COMMAND)
    interface DuplicateNameUseCase {
        Mono<String> other(String cmd, UseCaseMetadata metadata);
    }

    static class PlaceThing implements PlaceThingUseCase {
        @Override
        public Mono<String> handle(String cmd, UseCaseMetadata metadata) {
            return Mono.just(cmd);
        }
    }

    static class SpecialPlaceThing extends PlaceThing {}

    static class DuplicateName implements DuplicateNameUseCase {
        @Override
        public Mono<String> other(String cmd, UseCaseMetadata metadata) {
            return Mono.just(cmd);
        }
    }

    static class TwoUseCases implements PlaceThingUseCase, GetThingUseCase {
        @Override
        public Mono<String> handle(String cmd, UseCaseMetadata metadata) {
            return Mono.just(cmd);
        }

        @Override
        public Mono<String> find(String id, UseCaseMetadata metadata) {
            return Mono.just(id);
        }
    }

    @TransactionalUseCase
    static class TransactionalPlaceThing extends PlaceThing {}

    @TransactionalUseCase
    static class TransactionalGetThing implements GetThingUseCase {
        @Override
        public Mono<String> find(String id, UseCaseMetadata metadata) {
            return Mono.just(id);
        }
    }

    @TransactionalUseCase
    static class TransactionalPlain {}

    private static final UseCaseMetadata METADATA = new UseCaseMetadata(ActorType.UNKNOWN, "rest", "corr-1");

    private final TransactionalOperator tx = mock(TransactionalOperator.class);

    private final UseCasePipelinePostProcessor processor = new UseCasePipelinePostProcessor(
            new StaticListableBeanFactory(Map.of("observationRegistry", ObservationRegistry.NOOP))
                    .getBeanProvider(ObservationRegistry.class),
            new StaticListableBeanFactory(Map.of("transactionalOperator", tx))
                    .getBeanProvider(TransactionalOperator.class));

    @Test
    void wrapsBeanImplementingUseCaseInterface() {
        Object result = processor.postProcessAfterInitialization(new PlaceThing(), "placeThing");

        assertThat(UseCaseObservability.isObserved(result)).isTrue();
        assertThat(result).isInstanceOf(PlaceThingUseCase.class);
    }

    @Test
    void wrapsBeanInheritingUseCaseInterfaceFromSuperclass() {
        Object result = processor.postProcessAfterInitialization(new SpecialPlaceThing(), "specialPlaceThing");

        assertThat(UseCaseObservability.isObserved(result)).isTrue();
    }

    @Test
    void leavesOtherBeansUntouched() {
        Object bean = new Object();

        assertThat(processor.postProcessAfterInitialization(bean, "other")).isSameAs(bean);
    }

    @Test
    void doesNotWrapTwice() {
        Object first = processor.postProcessAfterInitialization(new PlaceThing(), "placeThing");

        assertThat(processor.postProcessAfterInitialization(first, "placeThing"))
                .isSameAs(first);
    }

    @Test
    void rejectsBeanWithTwoUseCaseInterfaces() {
        assertThatThrownBy(() -> processor.postProcessAfterInitialization(new TwoUseCases(), "twoUseCases"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(PlaceThingUseCase.class.getName())
                .hasMessageContaining(GetThingUseCase.class.getName());
    }

    @Test
    void rejectsDuplicateUseCaseName() {
        processor.postProcessAfterInitialization(new PlaceThing(), "placeThing");

        assertThatThrownBy(() -> processor.postProcessAfterInitialization(new DuplicateName(), "duplicateName"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(PlaceThingUseCase.class.getName())
                .hasMessageContaining(DuplicateNameUseCase.class.getName())
                .hasMessageContaining("placeThing")
                .hasMessageContaining("duplicateName");
    }

    @Test
    void rejectsSecondBeanForSameUseCase() {
        processor.postProcessAfterInitialization(new PlaceThing(), "placeThing");

        assertThatThrownBy(() -> processor.postProcessAfterInitialization(new PlaceThing(), "otherPlaceThing"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("thing.place")
                .hasMessageContaining("placeThing")
                .hasMessageContaining("otherPlaceThing")
                .hasMessageContaining(PlaceThingUseCase.class.getName());
    }

    @Test
    void runsLast() {
        assertThat(processor.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }

    @Test
    void runsTransactionalUseCaseInTransaction() {
        when(tx.transactional(any(Mono.class))).thenAnswer(inv -> inv.getArgument(0));
        PlaceThingUseCase wrapped = (PlaceThingUseCase)
                processor.postProcessAfterInitialization(new TransactionalPlaceThing(), "transactionalPlaceThing");

        assertThat(wrapped.handle("x", METADATA).block()).isEqualTo("x");

        verify(tx).transactional(any(Mono.class));
    }

    @Test
    void runsOtherUseCasesWithoutTransaction() {
        PlaceThingUseCase wrapped =
                (PlaceThingUseCase) processor.postProcessAfterInitialization(new PlaceThing(), "placeThing");

        assertThat(wrapped.handle("x", METADATA).block()).isEqualTo("x");

        verifyNoInteractions(tx);
    }

    @Test
    void rejectsTransactionalQueryUseCase() {
        assertThatThrownBy(() ->
                        processor.postProcessAfterInitialization(new TransactionalGetThing(), "transactionalGetThing"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(TransactionalGetThing.class.getName())
                .hasMessageContaining("COMMAND");
    }

    @Test
    void rejectsTransactionalClassWithoutUseCase() {
        assertThatThrownBy(
                        () -> processor.postProcessAfterInitialization(new TransactionalPlain(), "transactionalPlain"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(TransactionalPlain.class.getName())
                .hasMessageContaining("@UseCase");
    }
}
