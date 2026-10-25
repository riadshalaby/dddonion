package net.rsworld.example.dddonion.bootstrap.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.observation.ObservationRegistry;
import java.util.Map;
import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.architecture.UseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.Ordered;
import reactor.core.publisher.Mono;

class UseCaseObservabilityPostProcessorTest {

    @UseCase(name = "thing.place", kind = UseCase.Kind.COMMAND)
    interface PlaceThingUseCase {
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

    private final UseCaseObservabilityPostProcessor processor = new UseCaseObservabilityPostProcessor(
            new StaticListableBeanFactory(Map.of("observationRegistry", ObservationRegistry.NOOP))
                    .getBeanProvider(ObservationRegistry.class));

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
}
